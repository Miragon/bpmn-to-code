package io.miragon.bpmn.application.service

import io.miragon.bpmn.application.port.inbound.GenerateProcessApiInMemoryUseCase
import io.miragon.bpmn.application.port.outbound.ExtractBpmnPort
import io.miragon.bpmn.application.port.outbound.GenerateApiCodePort
import io.miragon.bpmn.domain.DuplicateProcessIdException
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.validation.BpmnValidationException
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class GenerateProcessApiInMemoryServiceTest {

    private val codeGenerator = mockk<GenerateApiCodePort>(relaxed = true)
    private val bpmnService = mockk<ExtractBpmnPort>(relaxed = true)

    private val underTest = GenerateProcessApiInMemoryService(codeGenerator = codeGenerator, bpmnService = bpmnService)

    @Test
    fun `service generates API files from BPMN content`() {
        // given: BPMN content
        val bpmnInput = GenerateProcessApiInMemoryUseCase.BpmnInput(
            bpmnXml = "<bpmn>test</bpmn>",
            processName = "test.bpmn",
        )
        val expectedGeneratedFile = GeneratedApiFile(
            fileName = "TestProcessApi.kt",
            packagePath = "com.example",
            content = "// generated code",
            language = OutputLanguage.KOTLIN,
            processId = "test",
        )
        every { bpmnService.extract(any(), any()) } returns dummyModel
        val sharedFile = GeneratedApiFile(
            fileName = "ServiceTasks.kt",
            packagePath = "com.example",
            content = "// generated code",
            language = OutputLanguage.KOTLIN,
            processId = null,
        )
        every { codeGenerator.generateCode(any()) } returns listOf(expectedGeneratedFile)
        every { codeGenerator.generateSharedCode(any()) } returns listOf(sharedFile)
        val command = GenerateProcessApiInMemoryUseCase.Command(
            bpmnContents = listOf(bpmnInput),
            packagePath = "com.example",
            outputLanguage = OutputLanguage.KOTLIN,
            engine = ProcessEngine.ZEEBE,
        )

        // when: generateProcessApi is called
        val result = underTest.generateProcessApi(command)

        // then: BpmnFile is created, models are extracted, code is generated
        verify { bpmnService.extract(match { it.fileName == "test.bpmn" }, eq(ProcessEngine.ZEEBE)) }
        verify { codeGenerator.generateCode(match { it.model.processId == dummyModel.processId }) }
        verify { codeGenerator.generateSharedCode(match { it.packagePath == "com.example" }) }
        assertThat(result).containsExactly(expectedGeneratedFile, sharedFile)
        confirmVerified(codeGenerator, bpmnService)
    }

    @Test
    fun `service rejects a model that targets a different engine before generating`() {
        // given: a model detected as Camunda 7 but generation requested for Operaton
        val bpmnInput = GenerateProcessApiInMemoryUseCase.BpmnInput(
            bpmnXml = "<bpmn>camunda</bpmn>",
            processName = "newsletter.bpmn",
        )
        every { bpmnService.extract(any(), any()) } returns dummyModel.copy(detectedEngine = ProcessEngine.CAMUNDA_7)
        val command = GenerateProcessApiInMemoryUseCase.Command(
            bpmnContents = listOf(bpmnInput),
            packagePath = "com.example",
            outputLanguage = OutputLanguage.KOTLIN,
            engine = ProcessEngine.OPERATON,
        )

        // when / then: it fails with a single engine-mismatch error and never generates code
        assertThatThrownBy { underTest.generateProcessApi(command) }
            .isInstanceOf(BpmnValidationException::class.java)
            .extracting("violations").matches { (it as List<*>).size == 1 }
        verify(exactly = 0) { codeGenerator.generateCode(any()) }
    }

    @Test
    fun `service rejects files sharing a process id unless variants are enabled`() {
        // given: two files defining the same process id
        every { bpmnService.extract(any(), any()) } returns dummyModel
        val command = GenerateProcessApiInMemoryUseCase.Command(
            bpmnContents = listOf(
                GenerateProcessApiInMemoryUseCase.BpmnInput(bpmnXml = "<bpmn>v1</bpmn>", processName = "v1.bpmn"),
                GenerateProcessApiInMemoryUseCase.BpmnInput(bpmnXml = "<bpmn>v2</bpmn>", processName = "v2.bpmn"),
            ),
            packagePath = "com.example",
            outputLanguage = OutputLanguage.KOTLIN,
            engine = ProcessEngine.ZEEBE,
        )

        // when / then: it fails naming both files and never generates code
        assertThatThrownBy { underTest.generateProcessApi(command) }
            .isInstanceOf(DuplicateProcessIdException::class.java).hasMessageContaining("v1.bpmn, v2.bpmn")
        verify(exactly = 0) { codeGenerator.generateCode(any()) }
    }

    @Test
    fun `service merges files sharing a process id into variants when enabled`() {
        // given: two variants of the same process and variants enabled
        every { bpmnService.extract(match { it.fileName == "v1.bpmn" }, any()) } returns dummyModel.copy(variantName = "v1")
        every { bpmnService.extract(match { it.fileName == "v2.bpmn" }, any()) } returns dummyModel.copy(variantName = "v2")
        val command = GenerateProcessApiInMemoryUseCase.Command(
            bpmnContents = listOf(
                GenerateProcessApiInMemoryUseCase.BpmnInput(bpmnXml = "<bpmn>v1</bpmn>", processName = "v1.bpmn"),
                GenerateProcessApiInMemoryUseCase.BpmnInput(bpmnXml = "<bpmn>v2</bpmn>", processName = "v2.bpmn"),
            ),
            packagePath = "com.example",
            outputLanguage = OutputLanguage.KOTLIN,
            engine = ProcessEngine.ZEEBE,
            enableVariants = true,
        )

        // when: generateProcessApi is called
        underTest.generateProcessApi(command)

        // then: a single merged model with both variants is generated
        verify(exactly = 1) {
            codeGenerator.generateCode(match { api -> api.model.variants.map { it.variantName } == listOf("v1", "v2") })
        }
    }

    @Test
    fun `service generates only the executable process when mixed`() {
        // given: one executable and one non-executable model
        every { bpmnService.extract(match { it.fileName == "keep.bpmn" }, any()) } returns dummyModel
        every { bpmnService.extract(match { it.fileName == "draft.bpmn" }, any()) } returns dummyModel.copy(processId = "draftProcess", isExecutable = false)
        val command = GenerateProcessApiInMemoryUseCase.Command(
            bpmnContents = listOf(
                GenerateProcessApiInMemoryUseCase.BpmnInput(bpmnXml = "<bpmn>keep</bpmn>", processName = "keep.bpmn"),
                GenerateProcessApiInMemoryUseCase.BpmnInput(bpmnXml = "<bpmn>draft</bpmn>", processName = "draft.bpmn"),
            ),
            packagePath = "com.example",
            outputLanguage = OutputLanguage.KOTLIN,
            engine = ProcessEngine.ZEEBE,
        )

        // when: generateProcessApi is called
        underTest.generateProcessApi(command)

        // then: only the executable process is generated, like on the filesystem path
        verify(exactly = 1) { codeGenerator.generateCode(any()) }
        verify { codeGenerator.generateCode(match { it.model.processId == "testProcess" }) }
    }

    private val dummyModel = ProcessModel(processId = "testProcess", flowNodes = emptyList())
}
