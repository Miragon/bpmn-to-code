package io.miragon.bpmn.adapter

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.nio.file.Files

class GradleIncrementalBuildSmokeTest {

    @Test
    fun `generateBpmnModelApi is up to date until a BPMN file changes`(@TempDir projectDir: File) {
        // given: a project that has generated its API once
        writeProject(projectDir)
        copyBpmn(projectDir = projectDir, model = "bike-leasing.bpmn", target = "src/main/resources/bike-leasing.bpmn")
        val firstRun = run(projectDir, API_TASK)

        // when: running again unchanged, then after editing a BPMN file, then after adding one in a nested folder
        val unchangedRun = run(projectDir, API_TASK)
        File(projectDir, "src/main/resources/bike-leasing.bpmn").appendText(BPMN_EDIT)
        val editedRun = run(projectDir, API_TASK)
        copyBpmn(projectDir = projectDir, model = "membership.bpmn", target = "src/main/resources/c8/membership.bpmn")
        val addedRun = run(projectDir, API_TASK)

        // then: only the runs with changed BPMN files execute the task
        assertThat(firstRun.task(":$API_TASK")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(unchangedRun.task(":$API_TASK")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        assertThat(editedRun.task(":$API_TASK")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(addedRun.task(":$API_TASK")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(File(projectDir, "build/generated/$PACKAGE_DIRECTORY/MembershipProcessApi.kt")).isFile()
    }

    @Test
    fun `generateBpmnModelJson is up to date until a BPMN file changes`(@TempDir projectDir: File) {
        // given: a project that has generated its JSON files once
        writeProject(projectDir)
        copyBpmn(projectDir = projectDir, model = "bike-leasing.bpmn", target = "src/main/resources/bike-leasing.bpmn")
        val firstRun = run(projectDir, JSON_TASK)

        // when: running again unchanged, then after adding a BPMN file
        val unchangedRun = run(projectDir, JSON_TASK)
        copyBpmn(projectDir = projectDir, model = "membership.bpmn", target = "src/main/resources/membership.bpmn")
        val addedRun = run(projectDir, JSON_TASK)

        // then: only the runs with changed BPMN files execute the task
        assertThat(firstRun.task(":$JSON_TASK")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(unchangedRun.task(":$JSON_TASK")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        assertThat(addedRun.task(":$JSON_TASK")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(requireNotNull(File(projectDir, "build/json").listFiles())).hasSize(2)
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = [API_TASK, JSON_TASK])
    fun `a generation into the build directory is restored from the build cache`(task: String, @TempDir projectDir: File) {
        // given: a project whose generated files were stored in the build cache and then deleted
        writeProject(projectDir)
        copyBpmn(projectDir = projectDir, model = "bike-leasing.bpmn", target = "src/main/resources/bike-leasing.bpmn")
        run(projectDir, task, "--build-cache")
        val generatedFilesBefore = generatedFileNames(projectDir)
        File(projectDir, "build/generated").deleteRecursively()
        File(projectDir, "build/json").deleteRecursively()

        // when: running the task again
        val result = run(projectDir, task, "--build-cache")

        // then: the generated files come back from the cache
        assertThat(result.task(":$task")?.outcome).isEqualTo(TaskOutcome.FROM_CACHE)
        assertThat(generatedFilesBefore).isNotEmpty()
        assertThat(generatedFileNames(projectDir)).isEqualTo(generatedFilesBefore)
    }

    @Test
    fun `generateBpmnModelApi notices changed BPMN files when the configuration cache is reused`(@TempDir projectDir: File) {
        // given: a project whose configuration is cached
        writeProject(projectDir)
        copyBpmn(projectDir = projectDir, model = "bike-leasing.bpmn", target = "src/main/resources/bike-leasing.bpmn")
        run(projectDir, API_TASK, "--configuration-cache")

        // when: running again unchanged, then after adding, editing and removing a BPMN file
        val unchangedRun = run(projectDir, API_TASK, "--configuration-cache")
        val membership = File(projectDir, "src/main/resources/membership.bpmn")
        copyBpmn(projectDir = projectDir, model = "membership.bpmn", target = "src/main/resources/membership.bpmn")
        val addedRun = run(projectDir, API_TASK, "--configuration-cache")
        membership.appendText(BPMN_EDIT)
        val editedRun = run(projectDir, API_TASK, "--configuration-cache")
        membership.delete()
        val removedRun = run(projectDir, API_TASK, "--configuration-cache")

        // then: every run reuses the configuration, and each change reruns the task
        val runs = listOf(unchangedRun, addedRun, editedRun, removedRun)
        assertThat(runs).allSatisfy { run -> assertThat(run.output).contains("Reusing configuration cache") }
        assertThat(unchangedRun.task(":$API_TASK")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        assertThat(addedRun.task(":$API_TASK")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(editedRun.task(":$API_TASK")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(removedRun.task(":$API_TASK")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(File(projectDir, "build/generated/$PACKAGE_DIRECTORY/MembershipProcessApi.kt")).doesNotExist()
    }

    @Test
    fun `generateBpmnModelApi resolves relative paths against the project and tracks exactly the named file`(@TempDir projectDir: File) {
        // given: relative folders and a file pattern without a wildcard
        writeProject(
            projectDir = projectDir,
            baseDir = "'.'",
            filePattern = "src/main/resources/bike-leasing.bpmn",
            outputFolderPath = "'build/generated'",
        )
        copyBpmn(projectDir = projectDir, model = "bike-leasing.bpmn", target = "src/main/resources/bike-leasing.bpmn")
        val firstRun = run(projectDir, API_TASK)

        // when: adding a BPMN file the pattern does not name
        copyBpmn(projectDir = projectDir, model = "membership.bpmn", target = "src/main/resources/membership.bpmn")
        val otherFileRun = run(projectDir, API_TASK)

        // then: the API lands in the project's build directory and the unrelated file does not rerun the task
        assertThat(firstRun.task(":$API_TASK")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(File(projectDir, "build/generated/$PACKAGE_DIRECTORY/BikeLeasingProcessApi.kt")).isFile()
        assertThat(otherFileRun.task(":$API_TASK")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
    }

    @Test
    fun `generateBpmnModelApi ignores BPMN files behind a symlinked directory like the generator does`(@TempDir projectDir: File) {
        // given: a generated project with a directory symlink in its BPMN folder
        writeProject(projectDir)
        copyBpmn(projectDir = projectDir, model = "bike-leasing.bpmn", target = "src/main/resources/bike-leasing.bpmn")
        copyBpmn(projectDir = projectDir, model = "membership.bpmn", target = "external/membership.bpmn")
        val link = File(projectDir, "src/main/resources/linked").toPath()
        Files.createSymbolicLink(link, File(projectDir, "external").toPath())
        run(projectDir, API_TASK)

        // when: editing the file behind the symlink
        File(projectDir, "external/membership.bpmn").appendText(BPMN_EDIT)
        val result = run(projectDir, API_TASK)

        // then: the file is neither generated nor tracked
        assertThat(File(projectDir, "build/generated/$PACKAGE_DIRECTORY/MembershipProcessApi.kt")).doesNotExist()
        assertThat(result.task(":$API_TASK")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
    }

    @Test
    fun `generateBpmnModelApi tracks only the BPMN files when generating into a shared source folder`(@TempDir projectDir: File) {
        // given: generation into src/main/kotlin, with hand-written files in, below and next to the generated package
        writeProject(projectDir = projectDir, outputFolderPath = "\"\${projectDir}/src/main/kotlin\"")
        copyBpmn(projectDir = projectDir, model = "bike-leasing.bpmn", target = "src/main/resources/bike-leasing.bpmn")
        val handWrittenFiles = writeHandWrittenSources(projectDir)

        // when: generating twice, then after editing the BPMN file, then after reverting that edit
        val bpmnFile = File(projectDir, "src/main/resources/bike-leasing.bpmn")
        val originalBpmn = bpmnFile.readBytes()
        val firstRun = run(projectDir, API_TASK, "--build-cache")
        val unchangedRun = run(projectDir, API_TASK, "--build-cache")
        bpmnFile.appendText(BPMN_EDIT)
        val editedRun = run(projectDir, API_TASK, "--build-cache")
        bpmnFile.writeBytes(originalBpmn)
        val revertedRun = run(projectDir, API_TASK, "--build-cache")

        // then: the task skips when nothing changed and otherwise regenerates rather than using the build cache,
        // and it never touches hand-written files
        assertThat(firstRun.task(":$API_TASK")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(unchangedRun.task(":$API_TASK")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        assertThat(editedRun.task(":$API_TASK")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(revertedRun.task(":$API_TASK")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(File(projectDir, "src/main/kotlin/$PACKAGE_DIRECTORY/BikeLeasingProcessApi.kt")).isFile()
        assertThat(handWrittenFiles).allSatisfy { file -> assertThat(file).hasContent(HAND_WRITTEN) }
    }

    @Test
    fun `generateBpmnModelApi reruns when the output moves to another source folder`(@TempDir projectDir: File) {
        // given: a project that has generated into src/main/kotlin
        writeProject(projectDir = projectDir, outputFolderPath = "\"\${projectDir}/src/main/kotlin\"")
        copyBpmn(projectDir = projectDir, model = "bike-leasing.bpmn", target = "src/main/resources/bike-leasing.bpmn")
        run(projectDir, API_TASK)

        // when: pointing the output at src/main/generated
        writeProject(projectDir = projectDir, outputFolderPath = "\"\${projectDir}/src/main/generated\"")
        val result = run(projectDir, API_TASK)

        // then: the API is generated into the new folder
        assertThat(result.task(":$API_TASK")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(File(projectDir, "src/main/generated/$PACKAGE_DIRECTORY/BikeLeasingProcessApi.kt")).isFile()
    }

    @Test
    fun `generateBpmnModelApi stores its configuration when values come from Gradle properties`(@TempDir projectDir: File) {
        // given: a project that reads its folders and package from Gradle properties with a fallback
        writeProject(
            projectDir = projectDir,
            baseDir = "providers.gradleProperty('bpmnBaseDir').orElse(projectDir.toString())",
            outputFolderPath = "providers.gradleProperty('bpmnOutput').orElse(\"\${projectDir}/build/generated\")",
            packagePath = "providers.gradleProperty('bpmnPackage').orElse('io.miragon.smoketest')",
        )
        copyBpmn(projectDir = projectDir, model = "bike-leasing.bpmn", target = "src/main/resources/bike-leasing.bpmn")

        // when: running twice with the configuration cache
        val firstRun = run(projectDir, API_TASK, "--configuration-cache")
        val unchangedRun = run(projectDir, API_TASK, "--configuration-cache")

        // then: the configuration is stored and reused
        assertThat(firstRun.task(":$API_TASK")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(unchangedRun.output).contains("Reusing configuration cache")
        assertThat(unchangedRun.task(":$API_TASK")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
    }

    @Test
    fun `a build that generates into a source folder runs tasks reading that folder without extra wiring`(@TempDir projectDir: File) {
        // given: the 5.x setup - generation into src/main/java before compileJava, plus a sources jar reading that folder
        File(projectDir, "settings.gradle").writeText("")
        File(projectDir, "build.gradle").writeText(
            """
            plugins {
                id 'java'
                id 'io.miragon.bpmn-to-code-gradle'
            }
            repositories {
                mavenLocal()
                mavenCentral()
            }
            java {
                withSourcesJar()
            }
            tasks.named('compileJava') {
                dependsOn tasks.named('$API_TASK')
            }
            tasks.named('$API_TASK') {
                baseDir = projectDir.toString()
                filePattern = 'src/main/resources/**/*.bpmn'
                outputFolderPath = "${'$'}{projectDir}/src/main/java"
                packagePath = 'io.miragon.smoketest'
                outputLanguage = io.miragon.bpmn.domain.shared.OutputLanguage.JAVA
                processEngine = io.miragon.bpmn.domain.shared.ProcessEngine.ZEEBE
            }
            """.trimIndent(),
        )
        copyBpmn(projectDir = projectDir, model = "bike-leasing.bpmn", target = "src/main/resources/bike-leasing.bpmn")

        // when: building twice
        val firstRun = run(projectDir, "build")
        val unchangedRun = run(projectDir, "build")

        // then: the build passes and the second run skips the generation
        assertThat(firstRun.task(":sourcesJar")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(unchangedRun.task(":$API_TASK")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["baseDir", "filePattern", "outputFolderPath", "packagePath"])
    fun `generateBpmnModelApi names an input that is not configured`(property: String, @TempDir projectDir: File) {
        // given: a project that leaves one property unset
        writeProject(projectDir)
        val buildFile = File(projectDir, "build.gradle")
        val apiTaskConfiguration = buildFile.readText().substringBefore("tasks.named('$JSON_TASK')")
        val withoutProperty = apiTaskConfiguration.lines().filterNot { it.trim().startsWith("$property =") }
        buildFile.writeText(withoutProperty.joinToString("\n"))

        // when: running the task
        val runner = GradleRunner.create().withProjectDir(projectDir).withPluginClasspath().withArguments(API_TASK)
        val result = runner.buildAndFail()

        // then: the failure names the missing input
        assertThat(result.output).contains("property '$property' doesn't have a configured value")
    }

    private fun writeHandWrittenSources(projectDir: File): List<File> {
        val handWrittenFiles = listOf(
            "src/main/kotlin/$PACKAGE_DIRECTORY/SamePackage.kt",
            "src/main/kotlin/$PACKAGE_DIRECTORY/worker/SubPackage.kt",
            "src/main/kotlin/io/miragon/OtherPackage.kt",
        ).map { File(projectDir, it) }
        handWrittenFiles.forEach { file ->
            file.parentFile.mkdirs()
            file.writeText(HAND_WRITTEN)
        }
        return handWrittenFiles
    }

    private fun generatedFileNames(projectDir: File): List<String> {
        val outputFolders = listOf(File(projectDir, "build/generated"), File(projectDir, "build/json"))
        val generatedFiles = outputFolders.flatMap { folder -> folder.walkTopDown().filter { it.isFile } }
        return generatedFiles.map { it.name }.sorted()
    }

    private fun run(projectDir: File, vararg arguments: String): BuildResult {
        val runner = GradleRunner.create().withProjectDir(projectDir).withPluginClasspath().withArguments(*arguments)
        return runner.build()
    }

    private fun copyBpmn(projectDir: File, model: String, target: String) {
        val bpmnStream = requireNotNull(javaClass.classLoader.getResourceAsStream("bpmn/zeebe/$model"))
        File(projectDir, target).apply { parentFile.mkdirs() }.writeBytes(bpmnStream.readBytes())
    }

    private fun writeProject(
        projectDir: File,
        baseDir: String = "projectDir.toString()",
        filePattern: String = "src/main/resources/**/*.bpmn",
        outputFolderPath: String = "\"\${projectDir}/build/generated\"",
        packagePath: String = "'io.miragon.smoketest'",
    ) {
        File(projectDir, "settings.gradle").writeText(
            """
            buildCache {
                local {
                    directory = file('build-cache')
                }
            }
            """.trimIndent(),
        )
        File(projectDir, "build.gradle").writeText(
            """
            plugins {
                id 'io.miragon.bpmn-to-code-gradle'
            }
            tasks.named('$API_TASK') {
                baseDir = $baseDir
                filePattern = '$filePattern'
                outputFolderPath = $outputFolderPath
                packagePath = $packagePath
                outputLanguage = io.miragon.bpmn.domain.shared.OutputLanguage.KOTLIN
                processEngine = io.miragon.bpmn.domain.shared.ProcessEngine.ZEEBE
            }
            tasks.named('$JSON_TASK') {
                baseDir = $baseDir
                filePattern = '$filePattern'
                outputFolderPath = "${'$'}{projectDir}/build/json"
                processEngine = io.miragon.bpmn.domain.shared.ProcessEngine.ZEEBE
            }
            """.trimIndent(),
        )
    }

    private companion object {
        const val API_TASK = "generateBpmnModelApi"
        const val JSON_TASK = "generateBpmnModelJson"
        const val PACKAGE_DIRECTORY = "io/miragon/smoketest"
        const val HAND_WRITTEN = "// hand-written"
        const val BPMN_EDIT = "<!-- edited -->"
    }
}
