/*
 * Writes the JVM and Android runtime classpaths of each library project to
 * build/binary-size/dependencies.json for scripts/binary-size-report.py.
 *
 *   ./gradlew --init-script scripts/binary-size-dependencies.init.gradle.kts \
 *     :datacapture:binarySizeDependencies --no-configuration-cache
 */

import groovy.json.JsonOutput
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier

val runtimeClasspaths =
  mapOf("jvm" to "jvmRuntimeClasspath", "android" to "androidRuntimeClasspath")

allprojects {
  afterEvaluate {
    val configurations =
      runtimeClasspaths.filterValues { project.configurations.findByName(it) != null }
    if (configurations.isEmpty()) return@afterEvaluate

    tasks.register("binarySizeDependencies") {
      group = "reporting"
      description = "Writes the resolved runtime classpaths used by the binary size report."
      val outputFile = layout.buildDirectory.file("binary-size/dependencies.json")
      outputs.file(outputFile)
      outputs.upToDateWhen { false }

      doLast {
        val report =
          configurations.mapValues { (_, configurationName) ->
            project.configurations
              .getByName(configurationName)
              .incoming
              .artifacts
              .artifacts
              .map { artifact ->
                val entry =
                  mutableMapOf<String, Any>(
                    "file" to artifact.file.absolutePath,
                    "bytes" to artifact.file.length(),
                  )
                when (val id = artifact.id.componentIdentifier) {
                  is ModuleComponentIdentifier -> {
                    entry["module"] = "${id.group}:${id.module}"
                    entry["version"] = id.version
                  }

                  is ProjectComponentIdentifier -> entry["module"] = "project${id.projectPath}"

                  else -> entry["module"] = artifact.file.name
                }
                entry
              }
              .distinctBy { it["file"] }
              .sortedBy { it["module"] as String }
          }
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(report)))
        println("Wrote $file")
      }
    }
  }
}
