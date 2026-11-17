#!/usr/bin/env python3
"""Report kotlin-fhir-data-capture binary sizes per target as JSON and Markdown.

Usage:
    python3 scripts/binary-size-report.py [--output binary-size.json] [--commit-sha SHA]
        [--compare baseline.json] [--markdown-output report.md]
"""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import glob
import gzip
import json
import os
import re
import struct
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path
from typing import Optional

METADATA_KEY = "metadata"

# Module directory -> root package of its public API.
MODULES = {
    "datacapture": "dev.ohs.fhir.datacapture",
}

ANDROID_MIN_API = "26"
ANDROID_ABI = "arm64-v8a"
DEX_FIELD_IDS_OFFSET = 0x50
DEX_METHOD_IDS_OFFSET = 0x58
DEX_CLASS_DEFS_OFFSET = 0x60

COMPOSE_RESOURCES_PREFIX = "composeResources/"
JS_MODULE_PREFIX = "kotlin-fhir-data-capture-"
TOP_DEPENDENCIES = 15


def _first_file(pattern: str) -> Optional[str]:
    matches = sorted(p for p in glob.glob(pattern, recursive=True) if os.path.isfile(p))
    return matches[0] if matches else None


def _gzip_size(data: bytes) -> int:
    return len(gzip.compress(data, compresslevel=6))


def analyze_jar(jar_path: str) -> dict:
    classes = class_bytes = class_compressed = 0
    resources = resource_bytes = 0
    with zipfile.ZipFile(jar_path, "r") as zf:
        for info in zf.infolist():
            if info.filename.endswith(".class"):
                classes += 1
                class_bytes += info.file_size
                class_compressed += info.compress_size
            elif info.filename.startswith(COMPOSE_RESOURCES_PREFIX) and not info.is_dir():
                resources += 1
                resource_bytes += info.file_size
    return {
        "jar_bytes": os.path.getsize(jar_path),
        "total_classes": classes,
        "total_uncompressed_bytes": class_bytes,
        "total_compressed_bytes": class_compressed,
        "compose_resources": {"file_count": resources, "uncompressed_bytes": resource_bytes},
    }


def _find_android_sdk_jars() -> tuple[Optional[str], Optional[str]]:
    sdk_candidates = [
        os.environ.get("ANDROID_HOME"),
        os.environ.get("ANDROID_SDK_ROOT"),
        os.path.expanduser("~/Library/Android/sdk"),
        "/usr/local/lib/android/sdk",
        os.path.expanduser("~/Android/Sdk"),
    ]
    for sdk in sdk_candidates:
        if not sdk or not os.path.isdir(sdk):
            continue
        d8_jars = sorted(glob.glob(os.path.join(sdk, "build-tools", "*", "lib", "d8.jar")))
        android_jars = sorted(
            glob.glob(os.path.join(sdk, "platforms", "android-*", "android.jar")),
            key=lambda p: [int(n) for n in re.findall(r"\d+", Path(p).parent.name)],
        )
        if d8_jars and android_jars:
            return d8_jars[-1], android_jars[-1]
    return None, None


def _parse_dex_zip(zip_path: str) -> dict:
    classes = methods = fields = compressed = uncompressed = 0
    with zipfile.ZipFile(zip_path, "r") as zf:
        for info in zf.infolist():
            if not info.filename.endswith(".dex"):
                continue
            compressed += info.compress_size
            uncompressed += info.file_size
            data = zf.read(info.filename)
            fields += struct.unpack_from("<I", data, DEX_FIELD_IDS_OFFSET)[0]
            methods += struct.unpack_from("<I", data, DEX_METHOD_IDS_OFFSET)[0]
            classes += struct.unpack_from("<I", data, DEX_CLASS_DEFS_OFFSET)[0]
    return {
        "total_classes": classes,
        "total_methods": methods,
        "total_fields": fields,
        "total_compressed_bytes": compressed,
        "total_uncompressed_bytes": uncompressed,
    }


def _unpack_aar(aar_path: str, out_dir: str) -> tuple[Optional[str], Optional[str]]:
    classes_jar = rules = None
    with zipfile.ZipFile(aar_path, "r") as zf:
        names = set(zf.namelist())
        os.makedirs(out_dir, exist_ok=True)
        if "classes.jar" in names:
            classes_jar = os.path.join(out_dir, "classes.jar")
            Path(classes_jar).write_bytes(zf.read("classes.jar"))
        if "proguard.txt" in names:
            rules = os.path.join(out_dir, "proguard.txt")
            Path(rules).write_bytes(zf.read("proguard.txt"))
    return classes_jar, rules


def analyze_aar(aar_path: str) -> dict:
    parts = {"classes_bytes": 0, "resources_bytes": 0, "assets_bytes": 0, "other_bytes": 0}
    with zipfile.ZipFile(aar_path, "r") as zf:
        for info in zf.infolist():
            if info.is_dir():
                continue
            if info.filename == "classes.jar" or info.filename.startswith("libs/"):
                parts["classes_bytes"] += info.file_size
            elif info.filename.startswith("res/"):
                parts["resources_bytes"] += info.file_size
            elif info.filename.startswith("assets/"):
                parts["assets_bytes"] += info.file_size
            else:
                parts["other_bytes"] += info.file_size
    return {"aar_bytes": os.path.getsize(aar_path), **parts}


def analyze_packaged_files(aar_paths: list[str]) -> dict:
    # R8 only measures code; native libraries and assets from AARs are packaged into the APK as is.
    native = assets = 0
    for path in aar_paths:
        with zipfile.ZipFile(path, "r") as zf:
            for info in zf.infolist():
                if info.filename.startswith(f"jni/{ANDROID_ABI}/") and info.filename.endswith(".so"):
                    native += info.file_size
                elif info.filename.startswith("assets/") and not info.is_dir():
                    assets += info.file_size
    return {"native_libs_bytes": native, "assets_bytes": assets}


def _run_d8(jar_path: str, d8_jar: str, android_jar: str, out_zip: str) -> dict:
    subprocess.run(
        [
            "java", "-Xmx3g", "-cp", d8_jar, "com.android.tools.r8.D8",
            "--release", "--min-api", ANDROID_MIN_API,
            "--lib", android_jar,
            "--output", out_zip,
            jar_path,
        ],
        check=True,
        capture_output=True,
    )
    return _parse_dex_zip(out_zip)


def _run_r8(
    program_jars: list[str],
    rule_files: list[str],
    module_pkg: str,
    d8_jar: str,
    android_jar: str,
    tmpdir: str,
) -> dict:
    # Keeping the whole public API makes this an upper bound: an app using every widget.
    rules_path = os.path.join(tmpdir, "r8.pro")
    Path(rules_path).write_text(
        "-dontwarn **\n"
        "-ignorewarnings\n"
        "-dontobfuscate\n"
        f"-keep public class {module_pkg}.** {{ public protected *; }}\n",
        encoding="utf-8",
    )
    out_zip = os.path.join(tmpdir, "r8.zip")
    cmd = [
        "java", "-Xmx4g", "-cp", d8_jar, "com.android.tools.r8.R8",
        "--release", "--min-api", ANDROID_MIN_API,
        "--output", out_zip,
        "--pg-conf", rules_path,
        "--lib", android_jar,
    ]
    for rules in rule_files:
        cmd.extend(["--pg-conf", rules])
    cmd.extend(program_jars)
    subprocess.run(cmd, check=True, capture_output=True)
    return _parse_dex_zip(out_zip)


def analyze_android(
    aar_path: str,
    module_pkg: str,
    dependencies: list[dict],
    d8_jar: Optional[str],
    android_jar: Optional[str],
) -> dict:
    entry = analyze_aar(aar_path)
    dependency_aars = [d["file"] for d in dependencies if d["file"].endswith(".aar") and os.path.isfile(d["file"])]
    entry["packaged_with_dependencies"] = analyze_packaged_files([aar_path, *dependency_aars])
    if not (d8_jar and android_jar):
        return entry

    with tempfile.TemporaryDirectory() as tmpdir:
        classes_jar, module_rules = _unpack_aar(aar_path, os.path.join(tmpdir, "module"))
        if not classes_jar:
            return entry
        try:
            entry["d8"] = _run_d8(classes_jar, d8_jar, android_jar, os.path.join(tmpdir, "d8.zip"))
        except subprocess.CalledProcessError as exc:
            print(f"⚠️  D8 failed for {aar_path}: {exc.stderr.decode()[-2000:]}", file=sys.stderr)

        if not dependencies:
            return entry

        program_jars = [classes_jar]
        rule_files = [module_rules] if module_rules else []
        for index, dep in enumerate(dependencies):
            path = dep["file"]
            if not os.path.isfile(path):
                continue
            if path.endswith(".aar"):
                jar, rules = _unpack_aar(path, os.path.join(tmpdir, f"dep{index}"))
                if jar:
                    program_jars.append(jar)
                if rules:
                    rule_files.append(rules)
            elif path.endswith(".jar"):
                program_jars.append(path)
        try:
            entry["r8_with_dependencies"] = _run_r8(
                program_jars, rule_files, module_pkg, d8_jar, android_jar, tmpdir
            )
        except subprocess.CalledProcessError as exc:
            print(f"⚠️  R8 failed for {aar_path}: {exc.stderr.decode()[-2000:]}", file=sys.stderr)
    return entry


def _analyze_web_dir(directory: str, extensions: tuple[str, ...], own_name: str) -> Optional[dict]:
    files = [
        p for p in glob.glob(os.path.join(directory, "**", "*"), recursive=True)
        if os.path.isfile(p) and p.endswith(extensions)
    ]
    if not files:
        return None
    total = total_gzip = own = own_gzip = 0
    for path in files:
        data = Path(path).read_bytes()
        size, gz = len(data), _gzip_size(data)
        total += size
        total_gzip += gz
        if os.path.basename(path).startswith(own_name):
            own += size
            own_gzip += gz
    return {
        "file_count": len(files),
        "total_uncompressed_bytes": total,
        "total_compressed_bytes": total_gzip,
        "module_uncompressed_bytes": own,
        "module_compressed_bytes": own_gzip,
    }


def analyze_web_artifacts(mod_dir: str, artifact_name: str) -> dict:
    # Development output is measured because the production library is dead-code eliminated
    # against an empty export list. It holds every linked dependency; Wasm links them into one
    # binary, so only JS has a module-only share.
    web: dict = {}
    targets = {"js": ("js", (".js",)), "wasm": ("wasmJs", (".wasm", ".mjs"))}
    for key, (target, extensions) in targets.items():
        directory = os.path.join(mod_dir, "build", "compileSync", target, "main", "developmentLibrary", "kotlin")
        if result := _analyze_web_dir(directory, extensions, f"{JS_MODULE_PREFIX}{artifact_name}."):
            web[key] = result
    return web


def analyze_native(mod_dir: str, name: str) -> dict:
    klib = _first_file(os.path.join(mod_dir, "build", "libs", f"{name}-iosArm64Main.klib"))
    return {"ios_arm64": {"klib_bytes": os.path.getsize(klib)}} if klib else {}


def load_dependencies(mod_dir: str) -> dict:
    path = os.path.join(mod_dir, "build", "binary-size", "dependencies.json")
    if not os.path.isfile(path):
        return {}
    return json.loads(Path(path).read_text(encoding="utf-8"))


def summarize_dependencies(entries: list[dict]) -> dict:
    modules = {}
    for entry in entries:
        if entry["module"].startswith("project:"):
            continue
        existing = modules.get(entry["module"])
        bytes_ = entry["bytes"] + (existing["bytes"] if existing else 0)
        modules[entry["module"]] = {"version": entry.get("version"), "bytes": bytes_}
    return {
        "count": len(modules),
        "total_bytes": sum(m["bytes"] for m in modules.values()),
        "modules": dict(sorted(modules.items())),
    }


def _run_cmd(cmd: list[str], cwd: str) -> Optional[str]:
    try:
        res = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, check=True, timeout=5)
        out = (res.stdout or res.stderr).strip()
        return out or None
    except Exception:
        return None


def collect_metadata(root: str, commit_sha: Optional[str] = None) -> dict:
    sha = (
        commit_sha
        or os.environ.get("COMMIT_SHA")
        or os.environ.get("GITHUB_SHA")
        or _run_cmd(["git", "rev-parse", "HEAD"], cwd=root)
    )
    meta: dict = {"timestamp_utc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")}
    if sha:
        meta["commit_sha"] = sha
    meta["repository"] = os.environ.get("GITHUB_REPOSITORY", "ohs-foundation/kotlin-fhir-data-capture")
    meta["server_url"] = os.environ.get("GITHUB_SERVER_URL", "https://github.com")
    if run_id := os.environ.get("GITHUB_RUN_ID"):
        meta["workflow_run_id"] = run_id

    toml_path = os.path.join(root, "gradle", "libs.versions.toml")
    if os.path.isfile(toml_path):
        toml_text = Path(toml_path).read_text(encoding="utf-8")
        for key, field in [
            ("kotlin", "kotlin_version"),
            ("compose-multiplatform", "compose_multiplatform_version"),
            ("android-gradle-plugin", "android_gradle_plugin_version"),
        ]:
            if m := re.search(rf'^{re.escape(key)}\s*=\s*"([^"]+)"', toml_text, re.M):
                meta[field] = m.group(1)

    wrapper_path = os.path.join(root, "gradle", "wrapper", "gradle-wrapper.properties")
    if os.path.isfile(wrapper_path):
        wrapper_text = Path(wrapper_path).read_text(encoding="utf-8")
        if m := re.search(r"gradle-([0-9.]+)-(bin|all)\.zip", wrapper_text):
            meta["gradle_version"] = m.group(1)

    if java_ver := _run_cmd(["java", "-version"], cwd=root):
        meta["java_version"] = java_ver.splitlines()[0]

    return meta


def build_report(root: str, commit_sha: Optional[str] = None, skip_android: bool = False) -> dict:
    report: dict = {METADATA_KEY: collect_metadata(root, commit_sha=commit_sha)}

    d8_jar, android_jar = (None, None) if skip_android else _find_android_sdk_jars()
    if not skip_android and not d8_jar:
        print("ℹ️  Android SDK build-tools not found; skipping D8/R8 DEX metrics.", file=sys.stderr)

    for mod, module_pkg in MODULES.items():
        mod_dir = os.path.join(root, mod)
        name = os.path.basename(mod)
        entry: dict = {}

        dependencies = load_dependencies(mod_dir)
        if dependencies:
            entry["dependencies"] = {
                target: summarize_dependencies(entries) for target, entries in dependencies.items()
            }
        else:
            print(f"ℹ️  No dependencies.json for {mod}; skipping dependency metrics.", file=sys.stderr)

        if jar := _first_file(os.path.join(mod_dir, "build", "libs", f"{name}-jvm*.jar")):
            entry["jvm"] = analyze_jar(jar)
        else:
            print(f"⚠️  JVM JAR not found for {mod}", file=sys.stderr)

        if aar := _first_file(os.path.join(mod_dir, "build", "outputs", "aar", "*.aar")):
            entry["android"] = analyze_android(
                aar, module_pkg, dependencies.get("android", []), d8_jar, android_jar
            )
        else:
            print(f"⚠️  AAR not found for {mod}", file=sys.stderr)

        if web := analyze_web_artifacts(mod_dir, name):
            entry["web"] = web
        if native := analyze_native(mod_dir, name):
            entry["native"] = native

        if entry:
            report[mod] = entry
    return report


def fmt_bytes(b: int) -> str:
    if abs(b) >= 1_048_576:
        return f"{b / 1_048_576:.2f} MB"
    if abs(b) >= 1_024:
        return f"{b / 1_024:.1f} KB"
    return f"{b} B"


def fmt_delta(current: Optional[int], baseline: Optional[int]) -> str:
    if current is None:
        return "removed"
    if baseline is None:
        return "new"
    delta = current - baseline
    if delta == 0:
        return "—"
    pct = "new" if baseline == 0 else f"{delta / baseline * 100:+.1f}%"
    sign = "+" if delta > 0 else ""
    return f"{sign}{fmt_bytes(delta)}, {pct}"


def _get(data: Optional[dict], *path: str) -> Optional[int]:
    for key in path:
        if not isinstance(data, dict) or key not in data:
            return None
        data = data[key]
    return data if isinstance(data, int) else None


SUMMARY_ROWS = [
    ("Android: R8 release DEX incl. dependencies", ("android", "r8_with_dependencies", "total_uncompressed_bytes"), "bytes"),
    ("Android: native libs incl. dependencies (arm64-v8a)", ("android", "packaged_with_dependencies", "native_libs_bytes"), "bytes"),
    ("Android: assets incl. dependencies", ("android", "packaged_with_dependencies", "assets_bytes"), "bytes"),
    ("Android: AAR", ("android", "aar_bytes"), "bytes"),
    ("Android: D8 DEX (module only)", ("android", "d8", "total_uncompressed_bytes"), "bytes"),
    ("Android: D8 method count (module only)", ("android", "d8", "total_methods"), "count"),
    ("Android: runtime dependencies", ("dependencies", "android", "total_bytes"), "bytes"),
    ("JVM: JAR", ("jvm", "jar_bytes"), "bytes"),
    ("JVM: Compose resources", ("jvm", "compose_resources", "uncompressed_bytes"), "bytes"),
    ("JVM: runtime dependencies", ("dependencies", "jvm", "total_bytes"), "bytes"),
    ("JS: total (gzip)", ("web", "js", "total_compressed_bytes"), "bytes"),
    ("JS: module only (gzip)", ("web", "js", "module_compressed_bytes"), "bytes"),
    ("Wasm: total (gzip)", ("web", "wasm", "total_compressed_bytes"), "bytes"),
    ("iOS: iosArm64 klib", ("native", "ios_arm64", "klib_bytes"), "bytes"),
]


def _fmt_value(value: int, unit: str) -> str:
    return fmt_bytes(value) if unit == "bytes" else f"{value:,}"


def _fmt_value_delta(current: Optional[int], baseline: Optional[int], unit: str) -> str:
    if unit == "bytes" or current is None or baseline is None:
        return fmt_delta(current, baseline)
    delta = current - baseline
    return "—" if delta == 0 else f"{delta:+,}"


def _render_summary(mod: str, current: dict, baseline: Optional[dict]) -> list[str]:
    has_baseline = baseline is not None
    header = "| Metric | Size | Baseline | Change |" if has_baseline else "| Metric | Size |"
    divider = "|---|---:|---:|---:|" if has_baseline else "|---|---:|"
    lines = [f"### `{mod}`", "", header, divider]
    for label, path, unit in SUMMARY_ROWS:
        value = _get(current, *path)
        base = _get(baseline, *path) if has_baseline else None
        if value is None and base is None:
            continue
        shown = _fmt_value(value, unit) if value is not None else "—"
        if has_baseline:
            base_shown = _fmt_value(base, unit) if base is not None else "—"
            lines.append(f"| {label} | {shown} | {base_shown} | {_fmt_value_delta(value, base, unit)} |")
        else:
            lines.append(f"| {label} | {shown} |")
    lines.append("")
    return lines


def _render_dependency_changes(current: dict, baseline: Optional[dict]) -> list[str]:
    if baseline is None:
        return []
    lines: list[str] = []
    for target in ("android", "jvm"):
        now = (current.get("dependencies", {}).get(target) or {}).get("modules", {})
        before = (baseline.get("dependencies", {}).get(target) or {}).get("modules", {})
        if not now and not before:
            continue
        rows = []
        for module in sorted(set(now) | set(before)):
            a, b = now.get(module), before.get(module)
            if a and not b:
                rows.append(f"| ➕ `{module}` | {a['version'] or ''} | +{fmt_bytes(a['bytes'])} |")
            elif b and not a:
                rows.append(f"| ➖ `{module}` | {b['version'] or ''} | -{fmt_bytes(b['bytes'])} |")
            elif a["version"] != b["version"] or a["bytes"] != b["bytes"]:
                version = a["version"] if a["version"] == b["version"] else f"{b['version']} → {a['version']}"
                rows.append(f"| 🔄 `{module}` | {version} | {fmt_delta(a['bytes'], b['bytes'])} |")
        if rows:
            lines += [
                "<details>",
                f"<summary>{target.upper()} dependency changes ({len(rows)})</summary>",
                "",
                "| Dependency | Version | Size change |",
                "|---|---|---:|",
                *rows,
                "",
                "</details>",
                "",
            ]
    return lines


def _render_top_dependencies(current: dict) -> list[str]:
    modules = (current.get("dependencies", {}).get("android") or {}).get("modules", {})
    if not modules:
        return []
    largest = sorted(modules.items(), key=lambda kv: kv[1]["bytes"], reverse=True)[:TOP_DEPENDENCIES]
    lines = [
        "<details>",
        f"<summary>Largest Android runtime dependencies (top {len(largest)} of {len(modules)})</summary>",
        "",
        "| Dependency | Version | Size |",
        "|---|---|---:|",
    ]
    lines += [f"| `{m}` | {info['version'] or ''} | {fmt_bytes(info['bytes'])} |" for m, info in largest]
    lines += ["", "</details>", ""]
    return lines


def render_markdown(report: dict, baseline: Optional[dict]) -> str:
    meta = report.get(METADATA_KEY, {})
    lines = ["## 📦 Binary size report", ""]
    if baseline is None:
        lines += ["_No baseline from `main` was found; showing absolute sizes only._", ""]
    elif base_sha := baseline.get(METADATA_KEY, {}).get("commit_sha"):
        lines += [f"Compared with `main` at {base_sha[:10]}.", ""]

    for mod in MODULES:
        if mod not in report:
            continue
        base_mod = baseline.get(mod) if baseline else None
        lines += _render_summary(mod, report[mod], base_mod)
        lines += _render_dependency_changes(report[mod], base_mod)
        lines += _render_top_dependencies(report[mod])

    lines += [
        "<sub>R8 estimate keeps the module's whole public API, so it is an upper bound for an app "
        "that uses every widget. Sizes are uncompressed unless marked gzip.</sub>",
    ]
    if sha := meta.get("commit_sha"):
        lines += ["", f"<sub>Commit {sha[:10]} · Kotlin {meta.get('kotlin_version', '?')} · "
                      f"Compose Multiplatform {meta.get('compose_multiplatform_version', '?')}</sub>"]
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", default=".", help="Repository root (default: current directory)")
    parser.add_argument("--output", default="binary-size.json", help="Where to write the JSON report")
    parser.add_argument("--commit-sha", help="Commit SHA to record in the report metadata")
    parser.add_argument("--compare", help="Baseline JSON report to diff against (ignored if missing)")
    parser.add_argument("--markdown-output", help="Where to write the Markdown report")
    parser.add_argument("--skip-android", action="store_true", help="Skip D8/R8 DEX analysis")
    args = parser.parse_args()

    root = os.path.abspath(args.root)
    report = build_report(root, commit_sha=args.commit_sha, skip_android=args.skip_android)
    Path(args.output).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {args.output}", file=sys.stderr)

    baseline = None
    if args.compare and os.path.isfile(args.compare):
        baseline = json.loads(Path(args.compare).read_text(encoding="utf-8"))

    markdown = render_markdown(report, baseline)
    if args.markdown_output:
        Path(args.markdown_output).write_text(markdown, encoding="utf-8")
        print(f"Wrote {args.markdown_output}", file=sys.stderr)
    else:
        print(markdown)
    return 0


if __name__ == "__main__":
    sys.exit(main())
