package io.miragon.bpmn.domain

import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.shared.SequenceFlowDefinition
import io.miragon.bpmn.domain.shared.SubProcessKind
import io.miragon.bpmn.domain.shared.TimerType
import io.miragon.bpmn.domain.shared.VariableDefinition
import io.miragon.bpmn.domain.shared.VariableDirection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProcessModelNormalizationTest {

    @Test
    fun `sorts all collections alphabetically by raw name`() {
        // given: model with unsorted elements
        val model = testProcessModel(
            processId = "test-process",
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(
                    id = "z-node",
                    variables = listOf(VariableDefinition("alphaVar", VariableDirection.INPUT)),
                ),
                FlowNodeDefinition.Unknown(
                    id = "a-node",
                    variables = listOf(VariableDefinition("zetaVar", VariableDirection.INPUT)),
                ),
                FlowNodeDefinition.Unknown(id = "m-node"),
            ),
            escalations = listOf(
                RootElementDefinition.Escalation(id = "ESC_Z", name = "zEscalation", code = "300"),
                RootElementDefinition.Escalation(id = "ESC_A", name = "aEscalation", code = "100"),
                RootElementDefinition.Escalation(id = "ESC_M", name = "mEscalation", code = "200"),
            ),
        )

        // when
        val normalized = model.normalized()

        // then: collections should be sorted independently by their own raw name
        assertThat(normalized.flowNodes.map { it.id }).containsExactly("a-node", "m-node", "z-node")
        assertThat(normalized.variables.map { it.getRawName() }).containsExactly("alphaVar", "zetaVar")
        assertThat(normalized.definitions.escalations.map { it.getRawName() }).containsExactly("aEscalation_100", "mEscalation_200", "zEscalation_300")
    }

    @Test
    fun `deduplicates all elements of a model`() {
        // given: a model with duplicates of various element types
        val timerFlowNode = FlowNodeDefinition.Event(
            id = "TIMER_1",
            shape = EventShape.INTERMEDIATE_CATCH_EVENT,
            eventDefinitions = listOf(EventDefinitionInstance.Timer(TimerType.DATE, "2024-01-01")),
        )
        val flow = SequenceFlowDefinition(id = "Flow_1", sourceRef = "node-1", targetRef = "TIMER_1")
        val model = testProcessModel(
            processId = "test-process",
            errors = listOf(
                RootElementDefinition.Error(id = "TEST_ERROR", name = "TEST_ERROR", code = "400"),
                RootElementDefinition.Error(id = "TEST_ERROR", name = "TEST_ERROR", code = "400"),
            ),
            signals = listOf(
                RootElementDefinition.Signal(id = "TEST_SIGNAL", name = "TEST_SIGNAL"),
                RootElementDefinition.Signal(id = "TEST_SIGNAL", name = "TEST_SIGNAL"),
            ),
            messages = listOf(
                RootElementDefinition.Message(id = "TEST_MESSAGE", name = "TEST_MESSAGE"),
                RootElementDefinition.Message(id = "TEST_MESSAGE", name = "TEST_MESSAGE"),
            ),
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "node-1"),
                FlowNodeDefinition.Unknown(id = "node-1"),
                timerFlowNode,
                timerFlowNode,
            ),
            sequenceFlows = listOf(flow, flow),
            escalations = listOf(
                RootElementDefinition.Escalation(id = "TEST_ESC", name = "TEST_ESC", code = "500"),
                RootElementDefinition.Escalation(id = "TEST_ESC", name = "TEST_ESC", code = "500"),
            ),
        )

        // when
        val normalized = model.normalized()

        // then: duplicates should be removed from all element types
        assertThat(normalized.definitions.errors).containsExactly(RootElementDefinition.Error(id = "TEST_ERROR", name = "TEST_ERROR", code = "400"))
        assertThat(normalized.definitions.signals).containsExactly(RootElementDefinition.Signal(id = "TEST_SIGNAL", name = "TEST_SIGNAL"))
        assertThat(normalized.definitions.messages).containsExactly(RootElementDefinition.Message(id = "TEST_MESSAGE", name = "TEST_MESSAGE"))
        assertThat(normalized.flowNodes).containsExactly(timerFlowNode, FlowNodeDefinition.Unknown(id = "node-1"))
        assertThat(normalized.sequenceFlows).containsExactly(flow)
        assertThat(normalized.definitions.escalations).containsExactly(RootElementDefinition.Escalation(id = "TEST_ESC", name = "TEST_ESC", code = "500"))
    }

    @Test
    fun `drops flow nodes without an id`() {
        // given
        val model = testProcessModel(flowNodes = listOf(FlowNodeDefinition.Unknown(id = null), FlowNodeDefinition.Unknown(id = "node-1")))

        // when
        val normalized = model.normalized()

        // then
        assertThat(normalized.flowNodes).containsExactly(FlowNodeDefinition.Unknown(id = "node-1"))
    }

    @Test
    fun `lists a node's repeated variables and boundary events once`() {
        // given
        val orderId = VariableDefinition("orderId", VariableDirection.INPUT)
        val task = jobWorkerTask(
            id = "Task_Ship",
            jobType = "ship",
            variables = listOf(orderId, orderId),
            boundaryEventRefs = listOf("Boundary_Timeout", "Boundary_Error", "Boundary_Timeout"),
        )

        // when
        val normalized = testProcessModel(flowNodes = listOf(task)).normalized()

        // then: boundary events are sorted too
        val normalizedTask = normalized.flowNodes.single() as FlowNodeDefinition.Activity.Task
        assertThat(normalizedTask.variables).containsExactly(orderId)
        assertThat(normalizedTask.boundaryEventRefs).containsExactly("Boundary_Error", "Boundary_Timeout")
    }

    @Test
    fun `normalizes the scope nested in a sub-process`() {
        // given: a sub-process whose children are unsorted and repeat an id
        val innerFlow = SequenceFlowDefinition(id = "Flow_Inner", sourceRef = "a-inner", targetRef = "z-inner")
        val subProcess = FlowNodeDefinition.Activity.SubProcess(
            id = "SubProcess_1",
            kind = SubProcessKind.PLAIN,
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "z-inner"),
                FlowNodeDefinition.Unknown(id = "a-inner"),
                FlowNodeDefinition.Unknown(id = "z-inner"),
            ),
            sequenceFlows = listOf(innerFlow, innerFlow),
        )

        // when
        val normalized = testProcessModel(flowNodes = listOf(subProcess)).normalized()

        // then
        val normalizedSubProcess = normalized.flowNodes.single() as FlowNodeDefinition.Activity.SubProcess
        assertThat(normalizedSubProcess.flowNodes.map { it.id }).containsExactly("a-inner", "z-inner")
        assertThat(normalizedSubProcess.sequenceFlows).containsExactly(innerFlow)
    }

    @Test
    fun `keeps root elements that share a name but have their own id`() {
        // given: a model whose modeller created two bpmn:Message elements with the same name — the common
        // result of typing the same name on two events instead of picking the existing message
        val model = testProcessModel(
            processId = "order-process",
            messages = listOf(
                RootElementDefinition.Message(id = "Message_1", name = "OrderPlaced"),
                RootElementDefinition.Message(id = "Message_2", name = "OrderPlaced"),
            ),
            signals = listOf(
                RootElementDefinition.Signal(id = "Signal_1", name = "OrderCancelled"),
                RootElementDefinition.Signal(id = "Signal_2", name = "OrderCancelled"),
            ),
        )

        // when
        val normalized = model.normalized()

        // then: both survive, so every messageRef emitted by the extractor still resolves in the registry
        assertThat(normalized.definitions.messages.map { it.id }).containsExactlyInAnyOrder("Message_1", "Message_2")
        assertThat(normalized.definitions.signals.map { it.id }).containsExactlyInAnyOrder("Signal_1", "Signal_2")
    }

    @Test
    fun `names the artifacts of a process after its process id`() {
        // given
        val model = testProcessModel(processId = "order-process")

        // when / then
        assertThat(model.apiName).isEqualTo("order-process")
    }

    @Test
    fun `leads the artifact name with the variant name`() {
        // given
        val model = testProcessModel(processId = "order-process", variantName = "corporate")

        // when / then
        assertThat(model.apiName).isEqualTo("corporate_order-process")
    }
}
