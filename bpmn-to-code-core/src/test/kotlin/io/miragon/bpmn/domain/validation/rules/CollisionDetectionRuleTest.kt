package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.jobWorkerTask
import io.miragon.bpmn.domain.shared.CallActivityDefinition
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.GatewayKind
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.shared.SequenceFlowDefinition
import io.miragon.bpmn.domain.shared.SubProcessKind
import io.miragon.bpmn.domain.shared.VariableDirection
import io.miragon.bpmn.domain.testProcessModel
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationViolation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CollisionDetectionRuleTest {

    private val underTest = CollisionDetectionRule()

    @Test
    fun `reports collision when different IDs normalize to same constant`() {
        // given: two flow nodes that differ only in separator
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "endEvent_complete"),
                FlowNodeDefinition.Unknown(id = "endEvent-complete"),
            ),
        )

        // when: validating
        val violations = violationsOf(model)

        // then: one error violation referencing conflicting IDs
        assertThat(violations).hasSize(1)
        assertThat(violations[0].severity).isEqualTo(Severity.ERROR)
        assertThat(violations[0].message).contains("conflicting IDs")
    }

    @Test
    fun `no violations when no collisions`() {
        // given: distinct names across all element types
        val model = testProcessModel(
            processId = "TestProcess",
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "Activity_Task1"),
                FlowNodeDefinition.Unknown(id = "Activity_Task2"),
                jobWorkerTask(id = "Task1", jobType = "newsletter.sendMail"),
                jobWorkerTask(id = "Task2", jobType = "newsletter.sendConfirmationMail"),
            ),
            messages = listOf(
                RootElementDefinition.Message(id = "Message_FormSubmitted", name = "Message_FormSubmitted"),
                RootElementDefinition.Message(id = "Message_SubscriptionConfirmed", name = "Message_SubscriptionConfirmed"),
            ),
        )

        // when / then: no violations
        assertThat(violationsOf(model)).isEmpty()
    }

    @Test
    fun `reports the same element id declared at the root and inside a subprocess`() {
        // given: a root node and a subprocess-interior node with the same id stay two nodes
        val model = testProcessModel(
            processId = "TestProcess",
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "Activity_SendMail"),
                FlowNodeDefinition.Activity.SubProcess(
                    id = "SubProcess_Retry",
                    kind = SubProcessKind.PLAIN,
                    flowNodes = listOf(FlowNodeDefinition.Unknown(id = "Activity_SendMail")),
                ),
            ),
        )

        // when / then: the flat FlowNodes object can emit the repeated id only once
        assertThat(violationsOf(model).map { it.message })
            .containsExactly("[FlowNode] 'ActivitySendMail' has conflicting IDs: Activity_SendMail, Activity_SendMail")
    }

    @Test
    fun `reports flow nodes that fold to the same object name`() {
        // given: flow nodes that differ only in case or separator
        val model = testProcessModel(
            processId = "TestProcess",
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "eventData"),
                FlowNodeDefinition.Unknown(id = "event-data"),
                FlowNodeDefinition.Unknown(id = "event_Data"),
            ),
        )

        // when
        val violations = violationsOf(model)

        // then: one violation groups all three ids under the folded object name
        assertThat(violations.map { it.processId }).containsExactly("TestProcess")
        assertThat(violations.map { it.message }).containsExactly("[FlowNode] 'EventData' has conflicting IDs: event-data, eventData, event_Data")
    }

    @Test
    fun `reports a leading separator and none as one object name`() {
        // given: two flow nodes whose ids keep distinct constants but fold to the same PascalCase object name
        val model = testProcessModel(
            flowNodes = listOf(FlowNodeDefinition.Unknown(id = "foo"), FlowNodeDefinition.Unknown(id = "-foo")),
        )

        // when / then
        assertThat(violationsOf(model).map { it.message }).containsExactly("[FlowNode] 'Foo' has conflicting IDs: -foo, foo")
    }

    @Test
    fun `ignores ids that only share an UPPER_SNAKE form but keep distinct object names`() {
        // given: fooBar and fooBAR would have collided as constants; as FlowNodes objects they are FooBar and FooBAR
        val model = testProcessModel(
            flowNodes = listOf(FlowNodeDefinition.Unknown(id = "fooBar"), FlowNodeDefinition.Unknown(id = "fooBAR")),
        )

        // when / then: the flat FlowNodes can hold both
        assertThat(violationsOf(model)).isEmpty()
    }

    @Test
    fun `ignores sequence flow ids, because outgoing flows are named after their targets`() {
        // given: a gateway with two outgoing flows whose ids differ only in separator
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Gateway(id = "split", kind = GatewayKind.EXCLUSIVE, outgoing = listOf("flow_yes", "flow-yes")),
                FlowNodeDefinition.Unknown(id = "a", outgoing = listOf("flow_no")),
                FlowNodeDefinition.Unknown(id = "b"),
            ),
            sequenceFlows = listOf(
                SequenceFlowDefinition(id = "flow_yes", sourceRef = "split", targetRef = "a"),
                SequenceFlowDefinition(id = "flow-yes", sourceRef = "split", targetRef = "b"),
                SequenceFlowDefinition(id = "flow_no", sourceRef = "a", targetRef = "b"),
            ),
        )

        // when / then: the flows become toA / toB, so their folding ids do not clash
        assertThat(violationsOf(model)).isEmpty()
    }

    @Test
    fun `reports call-activity mappings of one direction that fold to the same constant`() {
        // given: two input mappings whose targets normalize to the same constant, and an output with that name
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Activity.CallActivity(
                    id = "callChild",
                    definition = CallActivityDefinition(
                        id = "callChild",
                        calledElement = "child",
                        mappings = listOf(
                            CallActivityDefinition.Mapping(direction = VariableDirection.INPUT, source = "a", target = "childId"),
                            CallActivityDefinition.Mapping(direction = VariableDirection.INPUT, source = "b", target = "child_id"),
                            CallActivityDefinition.Mapping(direction = VariableDirection.OUTPUT, source = "c", target = "child-id"),
                        ),
                    ),
                ),
            ),
        )

        // when / then: the two inputs collide inside Inputs; the output lives in Outputs and does not
        assertThat(violationsOf(model).map { it.message })
            .containsExactly("[CallActivityMapping] 'CHILD_ID' has conflicting IDs: childId, child_id")
    }

    @Test
    fun `reports only the flow node collision of a model that also has colliding shared definitions`() {
        // given: collisions in flow nodes, messages and signals
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "Activity_Task1"),
                FlowNodeDefinition.Unknown(id = "endEvent_complete"),
                FlowNodeDefinition.Unknown(id = "endEvent-complete"),
            ),
            messages = listOf(
                RootElementDefinition.Message(id = "msg1", name = "message_sent"),
                RootElementDefinition.Message(id = "msg2", name = "message-sent"),
            ),
        )

        // when / then: messages are shared definitions, checked by SharedDefinitionCollisionRule
        assertThat(violationsOf(model).map { it.message })
            .containsExactly("[FlowNode] 'EndEventComplete' has conflicting IDs: endEvent-complete, endEvent_complete")
    }

    private fun violationsOf(model: ProcessModel): List<ValidationViolation> {
        val context = SingleModelValidationContext(model = model, engine = ProcessEngine.ZEEBE)
        return underTest.validate(context)
    }
}
