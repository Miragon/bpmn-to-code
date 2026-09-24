package io.miragon.bpmn.domain.service

import io.miragon.bpmn.domain.jobWorkerTask
import io.miragon.bpmn.domain.shared.CallActivityDefinition
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.GatewayKind
import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.shared.SequenceFlowDefinition
import io.miragon.bpmn.domain.shared.SubProcessKind
import io.miragon.bpmn.domain.shared.VariableDefinition
import io.miragon.bpmn.domain.shared.VariableDirection
import io.miragon.bpmn.domain.testProcessModel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CollisionDetectionServiceTest {

    private val underTest = CollisionDetectionService()

    @Test
    fun `findCollisions returns empty when no collisions exist`() {
        // given: a model with distinct constant names across all element types
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

        // when / then: no collisions are detected
        assertThat(underTest.findCollisions(model)).isEmpty()
    }

    @Test
    fun `findCollisions allows true duplicates with same original ID in definitions`() {
        // given: a model with exact duplicate root elements (same id)
        val model = testProcessModel(
            processId = "TestProcess",
            messages = listOf(
                RootElementDefinition.Message(id = "Message_Test", name = "Message_Test"),
                RootElementDefinition.Message(id = "Message_Test", name = "Message_Test"),
            ),
        )

        // when / then: true duplicates are not treated as collisions
        assertThat(underTest.findCollisions(model)).isEmpty()
    }

    @Test
    fun `findCollisions detects the same element id declared at the root and inside a subprocess`() {
        // given: merging keeps a root node and a subprocess-interior node with the same id as two nodes
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

        // when: checking for collisions
        val collisions = underTest.findCollisions(model)

        // then: the repeated id is reported as a FlowNode collision, since the flat Flow object can emit it only once
        assertThat(collisions).hasSize(1)
        assertThat(collisions[0].variableType).isEqualTo("FlowNode")
        assertThat(collisions[0].constantName).isEqualTo("ActivitySendMail")
        assertThat(collisions[0].conflictingIds).containsExactly("Activity_SendMail", "Activity_SendMail")
    }

    @Test
    fun `findCollisions detects flow nodes that fold to the same object name`() {
        // given: flow nodes that differ only in case or separator
        val model = testProcessModel(
            processId = "TestProcess",
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "eventData"),
                FlowNodeDefinition.Unknown(id = "event-data"),
                FlowNodeDefinition.Unknown(id = "event_Data"),
            ),
        )

        // when: checking for collisions
        val collisions = underTest.findCollisions(model)

        // then: one collision groups all three ids under the folded object name
        assertThat(collisions).hasSize(1)
        assertThat(collisions[0].variableType).isEqualTo("FlowNode")
        assertThat(collisions[0].constantName).isEqualTo("EventData")
        assertThat(collisions[0].conflictingIds).containsExactlyInAnyOrder("event-data", "eventData", "event_Data")
        assertThat(collisions[0].processId).isEqualTo("TestProcess")
    }

    @Test
    fun `findCollisions detects folding collision between a leading separator and none`() {
        // given: two flow nodes whose ids fold to the same PascalCase object name (Foo)
        val model = testProcessModel(
            processId = "TestProcess",
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "foo"),
                FlowNodeDefinition.Unknown(id = "-foo"),
            ),
        )

        // when: checking for collisions
        val collisions = underTest.findCollisions(model)

        // then: one collision is reported on the folded object-name basis
        assertThat(collisions).hasSize(1)
        assertThat(collisions[0].constantName).isEqualTo("Foo")
        assertThat(collisions[0].conflictingIds).containsExactlyInAnyOrder("-foo", "foo")
    }

    @Test
    fun `findCollisions ignores ids that only share an UPPER_SNAKE form but keep distinct object names`() {
        // given: fooBar and fooBAR would have collided as constants; as Flow objects they are FooBar and FooBAR
        val model = testProcessModel(
            processId = "TestProcess",
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "fooBar"),
                FlowNodeDefinition.Unknown(id = "fooBAR"),
            ),
        )

        // when / then: no collision, the flat Flow can hold both
        assertThat(underTest.findCollisions(model)).isEmpty()
    }

    @Test
    fun `findSharedCollisions detects collisions in Messages`() {
        // given: two messages that normalize to the same constant
        val model = testProcessModel(
            processId = "TestProcess",
            messages = listOf(
                RootElementDefinition.Message(id = "msg1", name = "message_formSubmitted"),
                RootElementDefinition.Message(id = "msg2", name = "message-formSubmitted"),
            ),
        )

        // when: checking for collisions
        val collisions = underTest.findSharedCollisions(listOf(model))

        // then: one Message collision is reported
        assertThat(collisions).hasSize(1)
        assertThat(collisions[0].variableType).isEqualTo("Message")
        assertThat(collisions[0].constantName).isEqualTo("MESSAGE_FORM_SUBMITTED")
    }

    @Test
    fun `findSharedCollisions detects collisions in ServiceTasks`() {
        // given: two service tasks with implementations that normalize to the same constant
        val model = testProcessModel(
            processId = "TestProcess",
            flowNodes = listOf(
                jobWorkerTask(id = "task1", jobType = "newsletter.sendMail"),
                jobWorkerTask(id = "task2", jobType = "newsletter_sendMail"),
            ),
        )

        // when: checking for collisions
        val collisions = underTest.findSharedCollisions(listOf(model))

        // then: one ServiceTask collision is reported
        assertThat(collisions).hasSize(1)
        assertThat(collisions[0].variableType).isEqualTo("ServiceTask")
        assertThat(collisions[0].constantName).isEqualTo("NEWSLETTER_SEND_MAIL")
    }

    @Test
    fun `findSharedCollisions detects collisions in Signals`() {
        // given: two signals that normalize to the same constant
        val model = testProcessModel(
            processId = "TestProcess",
            signals = listOf(
                RootElementDefinition.Signal(id = "sig1", name = "signal.complete"),
                RootElementDefinition.Signal(id = "sig2", name = "signal_complete"),
            ),
        )

        // when: checking for collisions
        val collisions = underTest.findSharedCollisions(listOf(model))

        // then: one Signal collision is reported
        assertThat(collisions).hasSize(1)
        assertThat(collisions[0].variableType).isEqualTo("Signal")
        assertThat(collisions[0].constantName).isEqualTo("SIGNAL_COMPLETE")
    }

    @Test
    fun `findSharedCollisions detects collisions in Errors`() {
        // given: two errors that normalize to the same constant
        val model = testProcessModel(
            processId = "TestProcess",
            errors = listOf(
                RootElementDefinition.Error(id = "err1", name = "Error_InvalidMail", code = "400"),
                RootElementDefinition.Error(id = "err2", name = "Error-InvalidMail", code = "400"),
            ),
        )

        // when: checking for collisions
        val collisions = underTest.findSharedCollisions(listOf(model))

        // then: one Error collision is reported
        assertThat(collisions).hasSize(1)
        assertThat(collisions[0].variableType).isEqualTo("Error")
        assertThat(collisions[0].constantName).isEqualTo("ERROR_INVALID_MAIL_400")
    }

    @Test
    fun `findCollisions detects variables colliding within one node but not across nodes`() {
        // given: one node declaring userId and user_id, and another node reusing userId
        val model = testProcessModel(
            processId = "TestProcess",
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(
                    id = "node1",
                    variables = listOf(
                        VariableDefinition(name = "userId", direction = VariableDirection.INPUT),
                        VariableDefinition(name = "user_id", direction = VariableDirection.INPUT),
                    ),
                ),
                FlowNodeDefinition.Unknown(
                    id = "node2",
                    variables = listOf(VariableDefinition(name = "user-id", direction = VariableDirection.OUTPUT)),
                ),
            ),
        )

        // when: checking for collisions
        val collisions = underTest.findCollisions(model)

        // then: only the per-node pair is reported; node2's variable lives in its own Variables holder
        assertThat(collisions).hasSize(1)
        assertThat(collisions[0].variableType).isEqualTo("Variable")
        assertThat(collisions[0].constantName).isEqualTo("USER_ID")
        assertThat(collisions[0].conflictingIds).containsExactlyInAnyOrder("userId", "user_id")
    }

    @Test
    fun `findCollisions detects sequence flows of one node that fold to the same property name`() {
        // given: a gateway with two outgoing flows whose ids differ only in separator
        val model = testProcessModel(
            processId = "TestProcess",
            flowNodes = listOf(
                FlowNodeDefinition.Gateway(id = "split", kind = GatewayKind.EXCLUSIVE, outgoing = listOf("flow_yes", "flow-yes")),
                FlowNodeDefinition.Unknown(id = "a", outgoing = listOf("flow_no")),
                FlowNodeDefinition.Unknown(id = "b"),
            ),
            sequenceFlows = listOf(
                SequenceFlowDefinition("flow_yes", "split", "a"),
                SequenceFlowDefinition("flow-yes", "split", "b"),
                SequenceFlowDefinition("flow_no", "a", "b"),
            ),
        )

        // when: checking for collisions
        val collisions = underTest.findCollisions(model)

        // then: one SequenceFlow collision on the gateway's Flows holder
        assertThat(collisions).hasSize(1)
        assertThat(collisions[0].variableType).isEqualTo("SequenceFlow")
        assertThat(collisions[0].constantName).isEqualTo("FlowYes")
        assertThat(collisions[0].conflictingIds).containsExactlyInAnyOrder("flow_yes", "flow-yes")
    }

    @Test
    fun `findCollisions detects call-activity mappings of one direction that fold to the same constant`() {
        // given: two input mappings whose targets normalize to the same constant
        val model = testProcessModel(
            processId = "TestProcess",
            flowNodes = listOf(
                FlowNodeDefinition.Activity.CallActivity(
                    id = "callChild",
                    definition = CallActivityDefinition(
                        id = "callChild",
                        calledElement = "child",
                        mappings = listOf(
                            CallActivityDefinition.Mapping(VariableDirection.INPUT, source = "a", target = "childId"),
                            CallActivityDefinition.Mapping(VariableDirection.INPUT, source = "b", target = "child_id"),
                            CallActivityDefinition.Mapping(VariableDirection.OUTPUT, source = "c", target = "child-id"),
                        ),
                    ),
                ),
            ),
        )

        // when: checking for collisions
        val collisions = underTest.findCollisions(model)

        // then: the two inputs collide inside Inputs; the output lives in Outputs and does not
        assertThat(collisions).hasSize(1)
        assertThat(collisions[0].variableType).isEqualTo("CallActivityMapping")
        assertThat(collisions[0].constantName).isEqualTo("CHILD_ID")
        assertThat(collisions[0].conflictingIds).containsExactlyInAnyOrder("childId", "child_id")
    }

    @Test
    fun `findCollisions detects multiple collisions across different variable types`() {
        // given: a model with collisions in FlowNodes, Messages, and Signals simultaneously
        val model = testProcessModel(
            processId = "TestProcess",
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "endEvent_complete"),
                FlowNodeDefinition.Unknown(id = "endEvent-complete"),
            ),
            messages = listOf(
                RootElementDefinition.Message(id = "msg1", name = "message_sent"),
                RootElementDefinition.Message(id = "msg2", name = "message-sent"),
            ),
            signals = listOf(
                RootElementDefinition.Signal(id = "sig1", name = "signal_ready"),
                RootElementDefinition.Signal(id = "sig2", name = "signal-ready"),
            ),
        )

        // when: checking for collisions
        val collisions = underTest.findCollisions(model)

        // then: the flow node collision is process-local, the message and signal collisions are shared
        assertThat(collisions.map { it.variableType }).containsExactly("FlowNode")
        assertThat(underTest.findSharedCollisions(listOf(model)).map { it.variableType }).containsExactlyInAnyOrder(
            "Message",
            "Signal",
        )
    }

    @Test
    fun `findCollisions handles mixed valid and collision cases`() {
        // given: a model where most nodes are unique but two share an object name
        val model = testProcessModel(
            processId = "TestProcess",
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "Activity_Task1"),
                FlowNodeDefinition.Unknown(id = "Activity_Task2"),
                FlowNodeDefinition.Unknown(id = "Activity_Task3"),
                FlowNodeDefinition.Unknown(id = "endEvent_complete"),
                FlowNodeDefinition.Unknown(id = "endEvent-complete"),
            ),
        )

        // when: checking for collisions
        val collisions = underTest.findCollisions(model)

        // then: only the colliding pair is reported
        assertThat(collisions).hasSize(1)
        assertThat(collisions[0].constantName).isEqualTo("EndEventComplete")
    }

    @Test
    fun `findSharedCollisions detects collisions in Escalations`() {
        // given: two escalations that normalize to the same constant
        val model = testProcessModel(
            processId = "TestProcess",
            escalations = listOf(
                RootElementDefinition.Escalation(id = "esc1", name = "notify.support", code = "200"),
                RootElementDefinition.Escalation(id = "esc2", name = "notify-support", code = "200"),
            ),
        )

        // when: checking for collisions
        val collisions = underTest.findSharedCollisions(listOf(model))

        // then: one Escalation collision is reported
        assertThat(collisions).hasSize(1)
        assertThat(collisions[0].variableType).isEqualTo("Escalation")
        assertThat(collisions[0].constantName).isEqualTo("NOTIFY_SUPPORT_200")
    }

    @Test
    fun `findSharedCollisions detects collisions across processes`() {
        // given: two processes whose job types normalize to the same constant
        val first = testProcessModel(
            processId = "first",
            flowNodes = listOf(jobWorkerTask(id = "task1", jobType = "newsletter.sendMail")),
        )
        val second = testProcessModel(
            processId = "second",
            flowNodes = listOf(jobWorkerTask(id = "task2", jobType = "newsletter-sendMail")),
        )

        // when: checking for collisions
        val collisions = underTest.findSharedCollisions(listOf(first, second))

        // then: one ServiceTask collision names both processes
        assertThat(collisions).hasSize(1)
        assertThat(collisions[0].variableType).isEqualTo("ServiceTask")
        assertThat(collisions[0].processId).isEqualTo("first, second")
        assertThat(collisions[0].conflictingIds).containsExactly("newsletter-sendMail", "newsletter.sendMail")
    }

    @Test
    fun `findSharedCollisions ignores the same identifier used by two processes`() {
        // given: two processes sharing the same job type
        val first = testProcessModel(processId = "first", flowNodes = listOf(jobWorkerTask(id = "task1", jobType = "newsletter.sendMail")))
        val second = testProcessModel(processId = "second", flowNodes = listOf(jobWorkerTask(id = "task2", jobType = "newsletter.sendMail")))

        // when / then: no collision is reported
        assertThat(underTest.findSharedCollisions(listOf(first, second))).isEmpty()
    }
}
