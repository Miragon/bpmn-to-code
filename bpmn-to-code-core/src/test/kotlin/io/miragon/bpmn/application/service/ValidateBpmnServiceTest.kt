package io.miragon.bpmn.application.service

import io.miragon.bpmn.application.port.inbound.ValidateBpmnFromFilesystemUseCase
import io.miragon.bpmn.application.port.outbound.ExtractBpmnPort
import io.miragon.bpmn.application.port.outbound.LoadBpmnFilesPort
import io.miragon.bpmn.domain.BpmnResource
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.shared.TaskImplementation
import io.miragon.bpmn.domain.shared.TaskKind
import io.miragon.bpmn.domain.testProcessModel
import io.miragon.bpmn.domain.validation.BpmnValidationException
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ValidateBpmnServiceTest {

    private val bpmnFileLoader = mockk<LoadBpmnFilesPort>()
    private val bpmnExtractor = mockk<ExtractBpmnPort>()

    private val underTest = ValidateBpmnService(bpmnFileLoader = bpmnFileLoader, bpmnService = bpmnExtractor)

    private val command = ValidateBpmnFromFilesystemUseCase.Command(
        baseDir = "baseDir",
        filePattern = "*.bpmn",
        engine = ProcessEngine.ZEEBE,
    )

    private val dummyResource = BpmnResource(fileName = "dummy.bpmn", content = "<bpmn></bpmn>".encodeToByteArray())

    @Test
    fun `accepts a valid model`() {
        // given: a valid model whose detected engine matches the selected one
        every { bpmnFileLoader.loadFrom(any(), any()) } returns listOf(dummyResource)
        every { bpmnExtractor.extract(any(), any()) } returns testProcessModel(detectedEngine = ProcessEngine.ZEEBE)

        // when / then
        assertThatCode { underTest.validateBpmn(command) }.doesNotThrowAnyException()
    }

    @Test
    fun `rejects a model the generation would reject`() {
        // given: a model with a service task missing implementation (an ERROR of a single-model rule)
        val invalidModel = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Activity.Task(
                    id = "task1",
                    kind = TaskKind.SERVICE,
                    implementation = TaskImplementation.Unspecified,
                ),
            ),
        )
        every { bpmnFileLoader.loadFrom(any(), any()) } returns listOf(dummyResource)
        every { bpmnExtractor.extract(any(), any()) } returns invalidModel

        // when / then
        assertThatThrownBy { underTest.validateBpmn(command) }
            .isInstanceOf(BpmnValidationException::class.java)
            .hasMessageContaining("missing-service-task-implementation")
    }
}
