package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.jobWorkerTask
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.shared.VariableDefinition
import io.miragon.bpmn.domain.shared.VariableDirection
import io.miragon.bpmn.domain.testProcessModel
import io.miragon.bpmn.domain.validation.model.CrossModelValidationContext
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.ValidationViolation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SharedDefinitionCollisionRuleTest {

    private val underTest = SharedDefinitionCollisionRule()

    @Test
    fun `is mandatory`() {
        assertThat(underTest.mandatory).isTrue()
    }

    @Test
    fun `reports messages of two processes that normalize to the same constant`() {
        // given: two processes whose message names differ only in separator
        val first = testProcessModel(processId = "first", messages = listOf(RootElementDefinition.Message(id = "m1", name = "order.created")))
        val second = testProcessModel(processId = "second", messages = listOf(RootElementDefinition.Message(id = "m2", name = "order-created")))

        // when: validating across both models
        val violations = violationsOf(first, second)

        // then: one error names both processes and both names
        assertThat(violations).hasSize(1)
        assertThat(violations[0].severity).isEqualTo(Severity.ERROR)
        assertThat(violations[0].processId).isEqualTo("first, second")
        assertThat(violations[0].message).isEqualTo("[Message] 'ORDER_CREATED' has conflicting IDs: order-created, order.created")
    }

    @Test
    fun `no violations when processes share the same identifier`() {
        // given: two processes using the same message
        val first = testProcessModel(processId = "first", messages = listOf(RootElementDefinition.Message(id = "m1", name = "order.created")))
        val second = testProcessModel(processId = "second", messages = listOf(RootElementDefinition.Message(id = "m2", name = "order.created")))

        // when / then: nothing is reported
        assertThat(violationsOf(first, second)).isEmpty()
    }

    @Test
    fun `allows true duplicates with the same id`() {
        val model = testProcessModel(
            messages = listOf(
                RootElementDefinition.Message(id = "Message_Test", name = "Message_Test"),
                RootElementDefinition.Message(id = "Message_Test", name = "Message_Test"),
            ),
        )

        assertThat(violationsOf(model)).isEmpty()
    }

    @Test
    fun `reports colliding messages`() {
        val model = testProcessModel(
            messages = listOf(
                RootElementDefinition.Message(id = "msg1", name = "message_formSubmitted"),
                RootElementDefinition.Message(id = "msg2", name = "message-formSubmitted"),
            ),
        )

        assertThat(violationsOf(model).map { it.message }).singleElement().asString().startsWith("[Message] 'FORM_SUBMITTED' ")
    }

    @Test
    fun `reports colliding job types`() {
        val model = testProcessModel(
            flowNodes = listOf(
                jobWorkerTask(id = "task1", jobType = "newsletter.sendMail"),
                jobWorkerTask(id = "task2", jobType = "newsletter_sendMail"),
            ),
        )

        assertThat(violationsOf(model).map { it.message }).singleElement().asString().startsWith("[ServiceTask] 'NEWSLETTER_SEND_MAIL' ")
    }

    @Test
    fun `reports colliding signals`() {
        val model = testProcessModel(
            signals = listOf(
                RootElementDefinition.Signal(id = "sig1", name = "signal.complete"),
                RootElementDefinition.Signal(id = "sig2", name = "signal_complete"),
            ),
        )

        assertThat(violationsOf(model).map { it.message }).singleElement().asString().startsWith("[Signal] 'COMPLETE' ")
    }

    @Test
    fun `reports colliding errors`() {
        val model = testProcessModel(
            errors = listOf(
                RootElementDefinition.Error(id = "err1", name = "Error_InvalidMail", code = "400"),
                RootElementDefinition.Error(id = "err2", name = "Error-InvalidMail", code = "400"),
            ),
        )

        assertThat(violationsOf(model).map { it.message }).singleElement().asString().startsWith("[Error] 'INVALID_MAIL' ")
    }

    @Test
    fun `reports errors with the same name and different codes`() {
        // given: two errors sharing a name, each with its own code
        val model = testProcessModel(
            errors = listOf(
                RootElementDefinition.Error(id = "err1", name = "InvalidMail", code = "400"),
                RootElementDefinition.Error(id = "err2", name = "InvalidMail", code = "500"),
            ),
        )

        // when / then: both would become the same constant
        assertThat(violationsOf(model).map { it.message }).singleElement().asString().startsWith("[Error] 'INVALID_MAIL' ")
    }

    @Test
    fun `reports colliding escalations`() {
        val model = testProcessModel(
            escalations = listOf(
                RootElementDefinition.Escalation(id = "esc1", name = "notify.support", code = "200"),
                RootElementDefinition.Escalation(id = "esc2", name = "notify-support", code = "200"),
            ),
        )

        assertThat(violationsOf(model).map { it.message }).singleElement().asString().startsWith("[Escalation] 'NOTIFY_SUPPORT' ")
    }

    @Test
    fun `reports colliding messages and signals of one model`() {
        val model = testProcessModel(
            messages = listOf(
                RootElementDefinition.Message(id = "msg1", name = "message_sent"),
                RootElementDefinition.Message(id = "msg2", name = "message-sent"),
            ),
            signals = listOf(
                RootElementDefinition.Signal(id = "sig1", name = "signal_ready"),
                RootElementDefinition.Signal(id = "sig2", name = "signal-ready"),
            ),
        )

        assertThat(violationsOf(model).map { it.message.substringBefore(']') }).containsExactlyInAnyOrder("[Message", "[Signal")
    }

    @Test
    fun `reports variables of different processes that fold to the same constant`() {
        // given: one process declaring userId and another declaring user_id
        val first = testProcessModel(
            processId = "first",
            flowNodes = listOf(FlowNodeDefinition.Unknown(id = "node1", variables = listOf(VariableDefinition(name = "userId", direction = VariableDirection.INPUT)))),
        )
        val second = testProcessModel(
            processId = "second",
            flowNodes = listOf(FlowNodeDefinition.Unknown(id = "node2", variables = listOf(VariableDefinition(name = "user_id", direction = VariableDirection.OUTPUT)))),
        )

        // when
        val violations = violationsOf(first, second)

        // then: both would become ProcessVariables.USER_ID
        assertThat(violations.map { it.processId }).containsExactly("first, second")
        assertThat(violations.map { it.message }).containsExactly("[Variable] 'USER_ID' has conflicting IDs: userId, user_id")
    }

    @Test
    fun `ignores the same variable declared by several nodes`() {
        // given: two nodes declaring userId with different directions
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "node1", variables = listOf(VariableDefinition(name = "userId", direction = VariableDirection.INPUT))),
                FlowNodeDefinition.Unknown(id = "node2", variables = listOf(VariableDefinition(name = "userId", direction = VariableDirection.OUTPUT))),
            ),
        )

        // when / then: one name, one constant
        assertThat(violationsOf(model)).isEmpty()
    }

    @Test
    fun `reports job types of two processes that fold to the same constant`() {
        // given: two processes whose job types normalize to the same constant
        val first = testProcessModel(processId = "first", flowNodes = listOf(jobWorkerTask(id = "task1", jobType = "newsletter.sendMail")))
        val second = testProcessModel(processId = "second", flowNodes = listOf(jobWorkerTask(id = "task2", jobType = "newsletter-sendMail")))

        // when
        val violations = violationsOf(first, second)

        // then: one violation names both processes
        assertThat(violations.map { it.processId }).containsExactly("first, second")
        assertThat(violations.map { it.message })
            .containsExactly("[ServiceTask] 'NEWSLETTER_SEND_MAIL' has conflicting IDs: newsletter-sendMail, newsletter.sendMail")
    }

    @Test
    fun `ignores the same job type used by two processes`() {
        val first = testProcessModel(processId = "first", flowNodes = listOf(jobWorkerTask(id = "task1", jobType = "newsletter.sendMail")))
        val second = testProcessModel(processId = "second", flowNodes = listOf(jobWorkerTask(id = "task2", jobType = "newsletter.sendMail")))

        assertThat(violationsOf(first, second)).isEmpty()
    }

    private fun violationsOf(vararg models: ProcessModel): List<ValidationViolation> {
        val context = CrossModelValidationContext(models.toList(), ProcessEngine.ZEEBE)
        return underTest.validate(context)
    }
}
