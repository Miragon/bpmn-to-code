package io.miragon.bpmn.testing

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BpmnRulesTest {

    @Test
    fun `all() returns all 10 built-in rules`() {
        val rules = BpmnRules.all()
        assertThat(rules).hasSize(10)
    }

    @Test
    fun `all rules have unique ids`() {
        val ids = BpmnRules.all().map { it.id }
        assertThat(ids).doesNotHaveDuplicates()
    }

    @Test
    fun `each constant has the expected rule id`() {
        assertThat(BpmnRules.MISSING_SERVICE_TASK_IMPLEMENTATION.id).isEqualTo("missing-service-task-implementation")
        assertThat(BpmnRules.MISSING_MESSAGE_NAME.id).isEqualTo("missing-message-name")
        assertThat(BpmnRules.MISSING_ERROR_DEFINITION.id).isEqualTo("missing-error-definition")
        assertThat(BpmnRules.MISSING_SIGNAL_NAME.id).isEqualTo("missing-signal-name")
        assertThat(BpmnRules.MISSING_TIMER_DEFINITION.id).isEqualTo("missing-timer-definition")
        assertThat(BpmnRules.MISSING_CALLED_ELEMENT.id).isEqualTo("missing-called-element")
        assertThat(BpmnRules.MISSING_ELEMENT_ID.id).isEqualTo("missing-element-id")
        assertThat(BpmnRules.EMPTY_PROCESS.id).isEqualTo("empty-process")
        assertThat(BpmnRules.MISSING_PROCESS_ID.id).isEqualTo("missing-process-id")
        assertThat(BpmnRules.COLLISION_DETECTION.id).isEqualTo("collision-detection")
    }

    @Test
    fun `constants are included in all()`() {
        val all = BpmnRules.all()
        assertThat(all).contains(
            BpmnRules.MISSING_SERVICE_TASK_IMPLEMENTATION,
            BpmnRules.MISSING_MESSAGE_NAME,
            BpmnRules.MISSING_ERROR_DEFINITION,
            BpmnRules.MISSING_SIGNAL_NAME,
            BpmnRules.MISSING_TIMER_DEFINITION,
            BpmnRules.MISSING_CALLED_ELEMENT,
            BpmnRules.MISSING_ELEMENT_ID,
            BpmnRules.EMPTY_PROCESS,
            BpmnRules.MISSING_PROCESS_ID,
            BpmnRules.COLLISION_DETECTION,
        )
    }

    @Test
    fun `optional rules are not part of all()`() {
        assertThat(BpmnRules.all()).doesNotContain(
            BpmnRules.TIMER_CRON_SYNTAX,
            BpmnRules.TIMER_ISO8601_SYNTAX,
        )
        assertThat(BpmnRules.all().map { it.id }).doesNotContain(
            BpmnRules.CALL_ACTIVITY_TARGET_EXISTS.id,
            BpmnRules.UNCAUGHT_MESSAGE_THROW.id,
            BpmnRules.UNCAUGHT_SIGNAL_THROW.id,
            BpmnRules.UNPUBLISHED_SIGNAL_CATCH.id,
        )
    }

    @Test
    fun `optional rule constants have the expected ids`() {
        assertThat(BpmnRules.TIMER_CRON_SYNTAX.id).isEqualTo("timer-cron-syntax")
        assertThat(BpmnRules.TIMER_ISO8601_SYNTAX.id).isEqualTo("timer-iso8601-syntax")
        assertThat(BpmnRules.CALL_ACTIVITY_TARGET_EXISTS.id).isEqualTo("call-activity-target-exists")
        assertThat(BpmnRules.UNCAUGHT_MESSAGE_THROW.id).isEqualTo("uncaught-message-throw")
        assertThat(BpmnRules.UNCAUGHT_SIGNAL_THROW.id).isEqualTo("uncaught-signal-throw")
        assertThat(BpmnRules.UNPUBLISHED_SIGNAL_CATCH.id).isEqualTo("unpublished-signal-catch")
    }
}
