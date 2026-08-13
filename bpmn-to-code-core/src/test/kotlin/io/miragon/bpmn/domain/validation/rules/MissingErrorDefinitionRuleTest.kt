package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.testProcessModel
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MissingErrorDefinitionRuleTest {

    private val underTest = MissingErrorDefinitionRule()

    private fun errorEvent(id: String, errorRef: String?, errorName: String?, errorCode: String?) = FlowNodeDefinition.Event(
        id = id,
        shape = EventShape.END_EVENT,
        eventDefinitions = listOf(EventDefinitionInstance.Error(errorRef, errorName, errorCode)),
    )

    private fun validate(node: FlowNodeDefinition) = underTest.validate(SingleModelValidationContext(model = testProcessModel(flowNodes = listOf(node)), engine = ProcessEngine.ZEEBE))

    @Test
    fun `reports error for an error event whose definition has no name`() {
        // given: an error event referencing an error root element but carrying no name
        val node = errorEvent(id = "errorEnd1", errorRef = "Error_1", errorName = null, errorCode = "500")

        // when / then: an ERROR violation is reported for the event node
        val violations = validate(node)
        assertThat(violations).hasSize(1)
        assertThat(violations[0].severity).isEqualTo(Severity.ERROR)
        assertThat(violations[0].elementId).isEqualTo("errorEnd1")
    }

    @Test
    fun `reports error for an error event whose definition has no code`() {
        // given: an error event referencing an error root element but carrying no code
        val node = errorEvent(id = "errorEnd1", errorRef = "Error_1", errorName = "MyError", errorCode = null)

        // when / then: a violation is reported
        val violations = validate(node)
        assertThat(violations).hasSize(1)
    }

    @Test
    fun `no violations for an error event with all fields`() {
        // given: a fully defined error event
        val node = errorEvent(id = "errorEnd1", errorRef = "Error_1", errorName = "MyError", errorCode = "500")

        // when / then: no violations
        assertThat(validate(node)).isEmpty()
    }

    @Test
    fun `does not flag a catch-all error event without an errorRef`() {
        // given: an error boundary event that catches any error (no errorRef, no name, no code)
        val node = errorEvent(id = "catchAll", errorRef = null, errorName = null, errorCode = null)

        // when / then: not flagged - a missing definition is only a problem when an error is actually referenced
        assertThat(validate(node)).isEmpty()
    }
}
