package io.miragon.bpmn.cli

import io.miragon.bpmn.adapter.inbound.CreateProcessApiFilesystemPlugin
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

class BpmnToCodeCliTest {

    private val underTest = BpmnToCodeCli

    @ParameterizedTest
    @EnumSource(OutputLanguage::class)
    fun `generate-api writes the same files as the plugin facade`(language: OutputLanguage, @TempDir tempDir: File) {
        // given: the files the facade writes for the same input
        val expectedDir = File(tempDir, "expected")
        val actualDir = File(tempDir, "actual")
        CreateProcessApiFilesystemPlugin().execute(
            baseDir = zeebeModelsDir,
            filePattern = ALL_MODELS,
            outputFolderPath = expectedDir.path,
            packagePath = PACKAGE_PATH,
            outputLanguage = language,
            engine = ProcessEngine.ZEEBE,
        )

        // when: the same generation is started from the command line
        val exitCode = underTest.execute(
            generateApiArguments(
                modelsDir = zeebeModelsDir,
                outputLanguage = language.name,
                outputFolderPath = actualDir.path,
            ),
        )

        // then: both runs wrote identical files
        assertThat(exitCode).isEqualTo(ExitCode.SUCCESS)
        assertThat(contentsOf(actualDir)).isNotEmpty.isEqualTo(contentsOf(expectedDir))
    }

    @Test
    fun `generate-api fails when a model cannot be read`(@TempDir tempDir: File) {
        // given: a file that is not well-formed XML
        File(tempDir, "broken.bpmn").writeText("<definitions")

        // when: the file is passed to the generator
        val exitCode = underTest.execute(
            generateApiArguments(
                modelsDir = tempDir.path,
                outputLanguage = OutputLanguage.KOTLIN.name,
                outputFolderPath = File(tempDir, "out").path,
            ),
        )

        // then: the run fails without being a usage error
        assertThat(exitCode).isEqualTo(ExitCode.FAILURE)
    }

    @ParameterizedTest
    @CsvSource(
        "'<!DOCTYPE definitions><definitions/>', DOCTYPE declarations are not allowed in BPMN files",
        "'<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"><unknown/></definitions>', DOM document is not valid",
    )
    fun `validate reports a rejected model as a message instead of a stack trace`(
        content: String,
        expectedMessage: String,
        @TempDir tempDir: File,
    ) {
        // given: a model the BPMN reader rejects
        File(tempDir, "rejected.bpmn").writeText(content)

        // when: the model is validated
        var exitCode = ExitCode.SUCCESS
        val standardError = captureStandardError {
            exitCode = underTest.execute(validateArguments(modelsDir = tempDir.path, filePattern = ALL_MODELS))
        }

        // then: the run fails with the reason on standard error
        assertThat(exitCode).isEqualTo(ExitCode.FAILURE)
        assertThat(standardError).contains(expectedMessage).doesNotContain("\tat ")
    }

    @Test
    fun `validate passes for models written for the selected engine`() {
        val exitCode = underTest.execute(validateArguments(modelsDir = zeebeModelsDir))
        assertThat(exitCode).isEqualTo(ExitCode.SUCCESS)
    }

    @Test
    fun `validate fails and reports the findings on standard error for models of another engine`() {
        // given: both output streams are captured
        val standardOutput = ByteArrayOutputStream()
        val standardError = ByteArrayOutputStream()
        val originalOutput = System.out
        val originalError = System.err
        System.setOut(PrintStream(standardOutput, true))
        System.setErr(PrintStream(standardError, true))

        // when: models are validated against an engine they were not written for
        val exitCode = try {
            underTest.execute(validateArguments(modelsDir = camunda7ModelsDir))
        } finally {
            System.setOut(originalOutput)
            System.setErr(originalError)
        }

        // then: the run fails and the findings go to standard error only
        assertThat(exitCode).isEqualTo(ExitCode.FAILURE)
        assertThat(standardError.toString()).contains("(rule: engine-mismatch)", "BPMN validation failed")
        assertThat(standardOutput.toString()).isEmpty()
    }

    @Test
    fun `validate skips disabled rules`() {
        val disabledRules = arrayOf("--disabledRules", "engine-mismatch,missing-called-element")
        val exitCode = underTest.execute(validateArguments(modelsDir = camunda7ModelsDir) + disabledRules)
        assertThat(exitCode).isEqualTo(ExitCode.SUCCESS)
    }

    @Test
    fun `rejects an unknown output language as a usage error`(@TempDir tempDir: File) {
        // when: an output language is passed that does not exist
        val outputDir = File(tempDir, "out")
        val exitCode = underTest.execute(
            generateApiArguments(
                modelsDir = zeebeModelsDir,
                outputLanguage = "COBOL",
                outputFolderPath = outputDir.path,
            ),
        )

        // then: nothing is generated and the call counts as wrong usage
        assertThat(exitCode).isEqualTo(ExitCode.USAGE_ERROR)
        assertThat(outputDir).doesNotExist()
    }

    @Test
    fun `rejects a missing required option as a usage error`() {
        val exitCode = underTest.execute(arrayOf("validate", "--baseDir", zeebeModelsDir))
        assertThat(exitCode).isEqualTo(ExitCode.USAGE_ERROR)
    }

    @Test
    fun `rejects a call without a command as a usage error`() {
        val exitCode = underTest.execute(emptyArray())
        assertThat(exitCode).isEqualTo(ExitCode.USAGE_ERROR)
    }

    @Test
    fun `prints the help`() {
        val exitCode = underTest.execute(arrayOf("--help"))
        assertThat(exitCode).isEqualTo(ExitCode.SUCCESS)
    }

    @Test
    fun `prints the version`() {
        val exitCode = underTest.execute(arrayOf("--version"))
        assertThat(exitCode).isEqualTo(ExitCode.SUCCESS)
    }

    private fun generateApiArguments(
        modelsDir: String,
        outputLanguage: String,
        outputFolderPath: String,
    ): Array<String> = arrayOf(
        "generate-api",
        "--baseDir", modelsDir,
        "--filePattern", ALL_MODELS,
        "--processEngine", ProcessEngine.ZEEBE.name,
        "--outputLanguage", outputLanguage,
        "--outputFolderPath", outputFolderPath,
        "--packagePath", PACKAGE_PATH,
    )

    private fun validateArguments(modelsDir: String, filePattern: String = BIKE_LEASING): Array<String> = arrayOf(
        "validate",
        "--baseDir",
        modelsDir,
        "--filePattern",
        filePattern,
        "--processEngine",
        ProcessEngine.ZEEBE.name,
    )

    private fun captureStandardError(action: () -> Unit): String {
        val standardError = ByteArrayOutputStream()
        val originalError = System.err
        System.setErr(PrintStream(standardError, true))
        try {
            action()
        } finally {
            System.setErr(originalError)
        }
        return standardError.toString()
    }

    private fun contentsOf(directory: File): Map<String, String> {
        val files = directory.walkTopDown().filter { file -> file.isFile }
        return files.associate { file -> file.relativeTo(directory).path to file.readText() }
    }

    private val zeebeModelsDir = File(System.getProperty("bpmn.models.dir"), "zeebe").path
    private val camunda7ModelsDir = File(System.getProperty("bpmn.models.dir"), "c7").path

    private companion object {
        const val ALL_MODELS = "*.bpmn"
        const val BIKE_LEASING = "bike-leasing.bpmn"
        const val PACKAGE_PATH = "io.miravelo.process"
    }
}
