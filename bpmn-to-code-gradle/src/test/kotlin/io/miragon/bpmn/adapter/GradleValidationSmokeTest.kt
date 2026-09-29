package io.miragon.bpmn.adapter

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File

class GradleValidationSmokeTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        "ZEEBE, zeebe/bike-leasing.bpmn",
        "CAMUNDA_7, c7/bike-leasing.bpmn",
        "OPERATON, operaton/bike-leasing.bpmn",
    )
    fun `validateBpmnModels succeeds for valid BPMN files`(
        engine: String,
        bpmnFile: String,
        @TempDir projectDir: File,
    ) {
        // given: a minimal project with a valid BPMN file for the given engine
        val resourcesDir = File(projectDir, "src/main/resources").also { it.mkdirs() }
        val bpmnStream = javaClass.classLoader.getResourceAsStream("bpmn/$bpmnFile")!!
        File(resourcesDir, File(bpmnFile).name).writeBytes(bpmnStream.readBytes())
        File(projectDir, "settings.gradle").writeText("")
        File(projectDir, "build.gradle").writeText(
            """
            plugins {
                id 'io.miragon.bpmn-to-code-gradle'
            }

            tasks.named('validateBpmnModels') {
                baseDir = projectDir.toString()
                filePattern = 'src/main/resources/*.bpmn'
                processEngine = io.miragon.bpmn.domain.shared.ProcessEngine.$engine
            }
            """.trimIndent(),
        )

        // when: running the validateBpmnModels task
        val result = GradleRunner.create()
            .withProjectDir(projectDir).withPluginClasspath().withArguments("validateBpmnModels").build()

        // then: the task succeeds with a validation passed message
        assertThat(result.task(":validateBpmnModels")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output).contains("BPMN validation passed")
    }

    @Test
    fun `validateBpmnModels names an input that is not configured`(@TempDir projectDir: File) {
        // given: a project that configures no process engine
        File(projectDir, "settings.gradle").writeText("")
        File(projectDir, "build.gradle").writeText(
            """
            plugins {
                id 'io.miragon.bpmn-to-code-gradle'
            }

            tasks.named('validateBpmnModels') {
                baseDir = projectDir.toString()
                filePattern = 'src/main/resources/*.bpmn'
            }
            """.trimIndent(),
        )

        // when: running the validateBpmnModels task
        val result = GradleRunner.create()
            .withProjectDir(projectDir).withPluginClasspath().withArguments("validateBpmnModels").buildAndFail()

        // then: the failure names the missing input
        assertThat(result.output).contains("processEngine has not been initialized")
    }
}
