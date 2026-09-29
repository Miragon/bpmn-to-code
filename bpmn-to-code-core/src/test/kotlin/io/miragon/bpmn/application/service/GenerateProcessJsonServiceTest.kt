package io.miragon.bpmn.application.service

import io.miragon.bpmn.application.port.inbound.GenerateProcessJsonFromFilesystemUseCase
import io.miragon.bpmn.application.port.outbound.ExtractBpmnPort
import io.miragon.bpmn.application.port.outbound.GenerateJsonPort
import io.miragon.bpmn.application.port.outbound.LoadBpmnFilesPort
import io.miragon.bpmn.application.port.outbound.SaveProcessJsonPort
import io.miragon.bpmn.domain.BpmnResource
import io.miragon.bpmn.domain.DuplicateProcessIdException
import io.miragon.bpmn.domain.GeneratedJsonFile
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.testProcessModel
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class GenerateProcessJsonServiceTest {

    private val jsonGenerator = mockk<GenerateJsonPort>(relaxed = true)
    private val bpmnFileLoader = mockk<LoadBpmnFilesPort>(relaxed = true)
    private val bpmnExtractor = mockk<ExtractBpmnPort>(relaxed = true)
    private val fileSaver = mockk<SaveProcessJsonPort>(relaxed = true)

    private val underTest = GenerateProcessJsonService(
        jsonGenerator = jsonGenerator,
        bpmnFileLoader = bpmnFileLoader,
        bpmnExtractor = bpmnExtractor,
        fileSaver = fileSaver,
    )

    @Test
    fun `generateProcessJson generates JSON and writes to disk`() {
        // given: a dummy BPMN resource and a command
        val dummyResource = BpmnResource(fileName = "dummy.bpmn", content = "<bpmn></bpmn>".encodeToByteArray())
        val expectedJsonFile = GeneratedJsonFile(fileName = "order.json", content = "{}")
        every { bpmnFileLoader.loadFrom("baseDir", "*.bpmn") } returns listOf(dummyResource)
        every { bpmnExtractor.extract(any(), any()) } returns dummyModel
        every { jsonGenerator.generateJson(any()) } returns expectedJsonFile
        val command = GenerateProcessJsonFromFilesystemUseCase.Command(
            baseDir = "baseDir",
            filePattern = "*.bpmn",
            engine = ProcessEngine.ZEEBE,
            outputFolderPath = "outputFolder",
        )

        // when: generateProcessJson is invoked
        underTest.generateProcessJson(command)

        // then: JSON is generated and written to disk
        verify { bpmnFileLoader.loadFrom("baseDir", "*.bpmn") }
        verify { jsonGenerator.generateJson(any()) }
        verify { fileSaver.writeFiles(listOf(expectedJsonFile), "outputFolder") }
        confirmVerified(jsonGenerator, bpmnFileLoader, fileSaver)
    }

    @Test
    fun `generateProcessJson rejects files sharing a process id unless variants are enabled`() {
        // given: two files defining the same process id
        every { bpmnFileLoader.loadFrom("baseDir", "*.bpmn") } returns listOf(resource("v1.bpmn"), resource("v2.bpmn"))
        every { bpmnExtractor.extract(any(), any()) } returns dummyModel

        // when / then: it fails naming both files and writes nothing
        assertThatThrownBy { underTest.generateProcessJson(command()) }
            .isInstanceOf(DuplicateProcessIdException::class.java)
            .hasMessageContaining("v1.bpmn, v2.bpmn")
        verify(exactly = 0) { fileSaver.writeFiles(any(), any()) }
    }

    @Test
    fun `generateProcessJson merges files sharing a process id into variants when enabled`() {
        // given: two variants of the same process and variants enabled
        every { bpmnFileLoader.loadFrom("baseDir", "*.bpmn") } returns listOf(resource("v1.bpmn"), resource("v2.bpmn"))
        every { bpmnExtractor.extract(match { it.fileName == "v1.bpmn" }, any()) } returns dummyModel.copy(variantName = "v1")
        every { bpmnExtractor.extract(match { it.fileName == "v2.bpmn" }, any()) } returns dummyModel.copy(variantName = "v2")

        // when: generateProcessJson is invoked
        underTest.generateProcessJson(command().copy(enableVariants = true))

        // then: JSON is generated once for the merged process
        verify(exactly = 1) { jsonGenerator.generateJson(match { it.isMerged }) }
    }

    private fun resource(fileName: String) = BpmnResource(fileName = fileName, content = "<bpmn></bpmn>".encodeToByteArray())

    private fun command() = GenerateProcessJsonFromFilesystemUseCase.Command(
        baseDir = "baseDir",
        filePattern = "*.bpmn",
        engine = ProcessEngine.ZEEBE,
        outputFolderPath = "outputFolder",
    )

    private val dummyModel = testProcessModel()
}
