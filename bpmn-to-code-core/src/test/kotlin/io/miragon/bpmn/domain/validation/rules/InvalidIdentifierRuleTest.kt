package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.shared.EscalationDefinition
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.FlowNodeProperties
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.shared.ServiceTaskDefinition
import io.miragon.bpmn.domain.shared.TimerDefinition
import io.miragon.bpmn.domain.testBpmnModel
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InvalidIdentifierRuleTest {

    private val underTest = InvalidIdentifierRule()

    @Test
    fun `no violations for flow node id that sanitizes to a valid identifier`() {

        // given: a flow node whose ID starts with a digit — sanitization prefixes an underscore
        val model = testBpmnModel(
            flowNodes = listOf(FlowNodeDefinition(id = "123-invalid"))
        )

        // when / then: sanitization yields _123_INVALID, so no violation is reported
        val violations = underTest.validate(SingleModelValidationContext(model = model, engine = ProcessEngine.ZEEBE))
        assertThat(violations).isEmpty()
    }

    @Test
    fun `no violations for connector service task type containing colons`() {

        // given: a connector service task whose type contains dots and colons (see issue #41)
        val model = testBpmnModel(
            flowNodes = listOf(
                FlowNodeDefinition(
                    id = "Task_LoadCustomer",
                    properties = FlowNodeProperties.ServiceTask(
                        ServiceTaskDefinition(
                            id = "Task_LoadCustomer",
                            engineSpecificProperties = mapOf(ServiceTaskDefinition.IMPL_VALUE_KEY to "io.camunda:http-json:1"),
                        )
                    ),
                )
            )
        )

        // when / then: sanitization yields IO_CAMUNDA_HTTP_JSON_1, so no violation is reported
        val violations = underTest.validate(SingleModelValidationContext(model = model, engine = ProcessEngine.ZEEBE))
        assertThat(violations).isEmpty()
    }

    @Test
    fun `no violations for escalation name that sanitizes to a valid identifier`() {

        // given: an escalation whose name starts with a digit
        val model = testBpmnModel(
            escalations = listOf(EscalationDefinition(id = "esc1", name = "123-escalation", code = "ESC"))
        )

        // when / then: sanitization yields _123_ESCALATION, so no violation is reported
        val violations = underTest.validate(SingleModelValidationContext(model = model, engine = ProcessEngine.ZEEBE))
        assertThat(violations).isEmpty()
    }

    @Test
    fun `no violations for valid identifiers`() {

        // given: a flow node with a valid ID
        val model = testBpmnModel(
            flowNodes = listOf(FlowNodeDefinition(id = "Activity_SendMail"))
        )

        // when / then: no violations
        val violations = underTest.validate(SingleModelValidationContext(model = model, engine = ProcessEngine.ZEEBE))
        assertThat(violations).isEmpty()
    }

    @Test
    fun `no violations for timer with valid identifier`() {

        // given: a timer flow node with a valid ID
        val model = testBpmnModel(
            flowNodes = listOf(
                FlowNodeDefinition(
                    id = "Timer_After3Days",
                    properties = FlowNodeProperties.Timer(TimerDefinition(id = "Timer_After3Days", type = "Duration", value = "PT1H")),
                )
            )
        )

        // when / then: no violations
        val violations = underTest.validate(SingleModelValidationContext(model = model, engine = ProcessEngine.ZEEBE))
        assertThat(violations).isEmpty()
    }
}
