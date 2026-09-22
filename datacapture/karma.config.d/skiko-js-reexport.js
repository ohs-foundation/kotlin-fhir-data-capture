;(function (config, __dirname) {
    var fs = require('fs')
    var path = require('path')

    // The Kotlin/JS `js` target's Skiko bridge (js-reexport-symbols.mjs) loads skiko.wasm and
    // assigns its exports onto `window` as globals. Nothing requires the file and Karma does not
    // serve it by default, so Compose UI tests fail with
    // "ReferenceError: org_jetbrains_skia_... is not defined" on first canvas use.
    var kotlinDir = path.join(__dirname, 'kotlin')
    var skikoReexportPath = path.join(kotlinDir, 'js-reexport-symbols.mjs')

    if (fs.existsSync(skikoReexportPath)) {
        // The skiko.wasm instantiation started by js-reexport-symbols.mjs is asynchronous. A
        // global `before()` hook, registered during Mocha's collection phase, gates the run on it
        // so tests do not race the load and fail with
        // "TypeError: org_jetbrains_skia_... is not a function".
        var skikoAwaitSetupPath = path.join(kotlinDir, 'skiko-await-setup.mjs')
        fs.writeFileSync(
            skikoAwaitSetupPath,
            'import { api } from "./js-reexport-symbols.mjs"\n' +
                'before(function () {\n' +
                '  return api.awaitSkiko\n' +
                '})\n',
        )

        config.set({
            files: [skikoReexportPath, skikoAwaitSetupPath].concat(config.files),
            preprocessors: Object.assign({}, config.preprocessors, {
                [skikoReexportPath]: ['webpack', 'sourcemap'],
                [skikoAwaitSetupPath]: ['webpack', 'sourcemap'],
            }),
        })
    }
})(config, __dirname)
