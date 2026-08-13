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
import io.miragon.bpmn.domain.validation.model.Severity
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ValidateBpmnServiceTest {

    private val bpmnFileLoader = mockk<LoadBpmnFilesPort>()
    private val bpmnExtractor = mockk<ExtractBpmnPort>()

    private val underTest = ValidateBpmnService(
        bpmnFileLoader = bpmnFileLoader,
        bpmnService = bpmnExtractor,
    )

    private val command = ValidateBpmnFromFilesystemUseCase.Command(
        baseDir = "baseDir",
        filePattern = "*.bpmn",
        engine = ProcessEngine.ZEEBE,
    )

    private val dummyResource = BpmnResource(fileName = "dummy.bpmn", content = "<bpmn></bpmn>".encodeToByteArray())

    @Test
    fun `valid model returns empty result`() {

        // given: a valid model whose detected engine matches the selected one
        every { bpmnFileLoader.loadFrom(any(), any()) } returns listOf(dummyResource)
        every { bpmnExtractor.extract(any(), any()) } returns testProcessModel(detectedEngine = ProcessEngine.ZEEBE)

        // when: validateBpmn is called
        val result = underTest.validateBpmn(command)

        // then: result has no violations
        assertThat(result.isValid).isTrue()
        assertThat(result.violations).isEmpty()
    }

    @Test
    fun `pre-merge error stops execution and returns early`() {

        // given: a model with a service task missing implementation (pre-merge ERROR)
        val invalidModel = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Activity.Task(
                    id = "task1",
                    kind = TaskKind.SERVICE,
                    implementation = TaskImplementation.Unspecified,
                )
            )
        )
        every { bpmnFileLoader.loadFrom(any(), any()) } returns listOf(dummyResource)
        every { bpmnExtractor.extract(any(), any()) } returns invalidModel

        // when: validateBpmn is called
        val result = underTest.validateBpmn(command)

        // then: result contains only pre-merge errors, no post-merge violations added
        assertThat(result.hasErrors).isTrue()
        assertThat(result.errors).anyMatch { it.severity == Severity.ERROR }
    }
}
