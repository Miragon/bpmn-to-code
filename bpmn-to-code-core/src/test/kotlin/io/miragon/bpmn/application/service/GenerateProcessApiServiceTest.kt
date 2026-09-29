package io.miragon.bpmn.application.service

import io.miragon.bpmn.adapter.outbound.filesystem.ProcessApiFileSaver
import io.miragon.bpmn.application.port.inbound.GenerateProcessApiFromFilesystemUseCase
import io.miragon.bpmn.application.port.outbound.ExtractBpmnPort
import io.miragon.bpmn.application.port.outbound.GenerateApiCodePort
import io.miragon.bpmn.application.port.outbound.LoadBpmnFilesPort
import io.miragon.bpmn.domain.BpmnFileResult
import io.miragon.bpmn.domain.BpmnResource
import io.miragon.bpmn.domain.DuplicateProcessIdException
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.testProcessModelApi
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
        fileSystemOutput = fileSystemOutput,
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
        val sharedFile = GeneratedApiFile(
            fileName = "ServiceTasks.kt",
            packagePath = "de.emaarco.example",
            content = "// generated code",
            language = OutputLanguage.KOTLIN,
            processId = null,
        )
        every { codeGenerator.generateCode(any()) } returns listOf(expectedGeneratedFile)
        every { codeGenerator.generateSharedCode(any()) } returns listOf(sharedFile)
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
        verify { codeGenerator.generateSharedCode(match { it.packagePath == "de.emaarco.example" && it.outputLanguage == OutputLanguage.KOTLIN }) }
        verify { fileSystemOutput.writeFiles(listOf(expectedGeneratedFile, sharedFile), "outputFolder") }
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

    @Test
    fun `generateProcessApi rejects files sharing a process id unless variants are enabled`() {
        // given: two files defining the same process id
        every { bpmnFileLoader.loadFrom("baseDir", "*.bpmn") } returns listOf(variantResource("v1.bpmn"), variantResource("v2.bpmn"))
        every { bpmnService.extract(any(), any()) } returns dummyModel

        // when / then: it fails naming both files and writes nothing
        assertThatThrownBy { underTest.generateProcessApi(command()) }
            .isInstanceOf(DuplicateProcessIdException::class.java)
            .hasMessageContaining("v1.bpmn, v2.bpmn")
        verify(exactly = 0) { fileSystemOutput.writeFiles(any(), any()) }
    }

    @Test
    fun `generateProcessApi merges files sharing a process id into variants when enabled`() {
        // given: two variants of the same process and variants enabled
        every { bpmnFileLoader.loadFrom("baseDir", "*.bpmn") } returns listOf(variantResource("v1.bpmn"), variantResource("v2.bpmn"))
        every { bpmnService.extract(match { it.fileName == "v1.bpmn" }, any()) } returns dummyModel.copy(variantName = "v1")
        every { bpmnService.extract(match { it.fileName == "v2.bpmn" }, any()) } returns dummyModel.copy(variantName = "v2")

        // when: generateProcessApi is invoked
        val results = underTest.generateProcessApi(command().copy(enableVariants = true))

        // then: one merged process backed by both files
        assertThat(results).containsExactly(BpmnFileResult(dummyModel.processId, listOf("v1.bpmn", "v2.bpmn")))
    }

    private fun variantResource(fileName: String) = BpmnResource(fileName = fileName, content = "<bpmn></bpmn>".encodeToByteArray())

    private val dummyModel = ProcessModel(
        processId = "newsletterSubscription",
        flowNodes = emptyList(),
    )

    private val nonExecutableModel = ProcessModel(
        processId = "draftProcess",
        flowNodes = emptyList(),
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

    private fun getExpectedModelApi() = testProcessModelApi(
        model = dummyModel,
        packagePath = "de.emaarco.example",
        language = OutputLanguage.KOTLIN,
    )
}
