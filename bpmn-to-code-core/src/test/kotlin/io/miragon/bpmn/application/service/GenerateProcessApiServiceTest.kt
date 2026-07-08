package io.miragon.bpmn.application.service

import io.miragon.bpmn.adapter.outbound.filesystem.ProcessApiFileSaver
import io.miragon.bpmn.domain.BpmnFileResult
import io.miragon.bpmn.application.port.inbound.GenerateProcessApiFromFilesystemUseCase
import io.miragon.bpmn.application.port.outbound.ExtractBpmnPort
import io.miragon.bpmn.application.port.outbound.GenerateApiCodePort
import io.miragon.bpmn.application.port.outbound.LoadBpmnFilesPort
import io.miragon.bpmn.domain.BpmnModel
import io.miragon.bpmn.domain.BpmnResource
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.testBpmnModelApi
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GenerateProcessApiServiceTest {

    private val codeGenerator = mockk<GenerateApiCodePort>(relaxed = true)
    private val bpmnFileLoader = mockk<LoadBpmnFilesPort>(relaxed = true)
    private val bpmnService = mockk<ExtractBpmnPort>(relaxed = true)
    private val fileSystemOutput = mockk<ProcessApiFileSaver>(relaxed = true)

    private val underTest = GenerateProcessApiService(
        codeGenerator = codeGenerator,
        bpmnFileLoader = bpmnFileLoader,
        bpmnService = bpmnService,
        fileSystemOutput = fileSystemOutput
    )

    @Test
    fun `generateProcessApi generates API file`() {

        // given: a dummy BPMN resource and a command
        val dummyResource = BpmnResource(
            fileName = "dummy.bpmn",
            content = "<bpmn></bpmn>".encodeToByteArray(),
        )
        val expectedGeneratedFile = GeneratedApiFile(
            fileName = "NewsletterSubscriptionProcessApi.kt",
            packagePath = "de.emaarco.example",
            content = "// generated code",
            language = OutputLanguage.KOTLIN,
            processId = "newsletterSubscription",
        )
        every { bpmnFileLoader.loadFrom("baseDir", "*.bpmn") } returns listOf(dummyResource)
        every { bpmnService.extract(any(), any()) } returns dummyModel
        every { codeGenerator.generateCode(any()) } returns listOf(expectedGeneratedFile)
        val command = GenerateProcessApiFromFilesystemUseCase.Command(
            baseDir = "baseDir",
            filePattern = "*.bpmn",
            engine = ProcessEngine.ZEEBE,
            outputFolderPath = "outputFolder",
            outputLanguage = OutputLanguage.KOTLIN,
            packagePath = "de.emaarco.example",
        )

        // when: generateProcessApi is invoked
        val results = underTest.generateProcessApi(command)

        // then: the API code is generated and written to disk
        val expectedModelApi = getExpectedModelApi()
        verify { bpmnFileLoader.loadFrom("baseDir", "*.bpmn") }
        verify { codeGenerator.generateCode(expectedModelApi) }
        verify { fileSystemOutput.writeFiles(listOf(expectedGeneratedFile), "outputFolder") }
        confirmVerified(codeGenerator, bpmnFileLoader, fileSystemOutput)
        assertThat(results).isEqualTo(listOf(BpmnFileResult(processId = "newsletterSubscription", sourceFiles = listOf("dummy.bpmn"))))
    }

    @Test
    fun `generateProcessApi skips a non-executable process`() {

        // given: a single non-executable model
        val draftResource = BpmnResource(fileName = "draft.bpmn", content = "<bpmn></bpmn>".encodeToByteArray())
        every { bpmnFileLoader.loadFrom("baseDir", "*.bpmn") } returns listOf(draftResource)
        every { bpmnService.extract(any(), any()) } returns nonExecutableModel

        // when: generateProcessApi is invoked
        val results = underTest.generateProcessApi(command())

        // then: nothing is generated, written or reported
        assertThat(results).isEmpty()
        verify(exactly = 0) { codeGenerator.generateCode(any()) }
        verify { fileSystemOutput.writeFiles(emptyList(), "outputFolder") }
    }

    @Test
    fun `generateProcessApi generates only the executable process when mixed`() {

        // given: one executable and one non-executable model
        val keepResource = BpmnResource(fileName = "keep.bpmn", content = "<bpmn></bpmn>".encodeToByteArray())
        val draftResource = BpmnResource(fileName = "draft.bpmn", content = "<bpmn></bpmn>".encodeToByteArray())
        val expectedGeneratedFile = GeneratedApiFile(
            fileName = "NewsletterSubscriptionProcessApi.kt",
            packagePath = "de.emaarco.example",
            content = "// generated code",
            language = OutputLanguage.KOTLIN,
            processId = "newsletterSubscription",
        )
        every { bpmnFileLoader.loadFrom("baseDir", "*.bpmn") } returns listOf(keepResource, draftResource)
        every { bpmnService.extract(keepResource, any()) } returns dummyModel
        every { bpmnService.extract(draftResource, any()) } returns nonExecutableModel
        every { codeGenerator.generateCode(any()) } returns listOf(expectedGeneratedFile)

        // when: generateProcessApi is invoked
        val results = underTest.generateProcessApi(command())

        // then: only the executable process is generated and reported
        verify(exactly = 1) { codeGenerator.generateCode(getExpectedModelApi()) }
        verify { fileSystemOutput.writeFiles(listOf(expectedGeneratedFile), "outputFolder") }
        assertThat(results).isEqualTo(listOf(BpmnFileResult(processId = "newsletterSubscription", sourceFiles = listOf("keep.bpmn"))))
    }

    @Test
    fun `generateProcessApi produces no output when all processes are non-executable`() {

        // given: only non-executable models
        val draftOne = BpmnResource(fileName = "draft-1.bpmn", content = "<bpmn></bpmn>".encodeToByteArray())
        val draftTwo = BpmnResource(fileName = "draft-2.bpmn", content = "<bpmn></bpmn>".encodeToByteArray())
        every { bpmnFileLoader.loadFrom("baseDir", "*.bpmn") } returns listOf(draftOne, draftTwo)
        every { bpmnService.extract(any(), any()) } returns nonExecutableModel

        // when: generateProcessApi is invoked
        val results = underTest.generateProcessApi(command())

        // then: generation completes with no output and no crash
        assertThat(results).isEmpty()
        verify(exactly = 0) { codeGenerator.generateCode(any()) }
        verify { fileSystemOutput.writeFiles(emptyList(), "outputFolder") }
    }

    private val dummyModel = BpmnModel(
        processId = "newsletterSubscription",
        flowNodes = emptyList(),
        messages = emptyList(),
        signals = emptyList(),
        errors = emptyList(),
    )

    private val nonExecutableModel = BpmnModel(
        processId = "draftProcess",
        flowNodes = emptyList(),
        messages = emptyList(),
        signals = emptyList(),
        errors = emptyList(),
        isExecutable = false,
    )

    private fun command() = GenerateProcessApiFromFilesystemUseCase.Command(
        baseDir = "baseDir",
        filePattern = "*.bpmn",
        engine = ProcessEngine.ZEEBE,
        outputFolderPath = "outputFolder",
        outputLanguage = OutputLanguage.KOTLIN,
        packagePath = "de.emaarco.example",
    )

    private fun getExpectedModelApi() = testBpmnModelApi(
        model = dummyModel,
        packagePath = "de.emaarco.example",
        language = OutputLanguage.KOTLIN
    )

}
