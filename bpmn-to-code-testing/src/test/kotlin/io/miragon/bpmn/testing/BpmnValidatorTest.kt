package io.miragon.bpmn.testing

import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationViolation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class BpmnValidatorTest {

    @Test
    fun `valid bpmn passes assertNoErrors`() {
        BpmnValidator
            .fromClasspath("bpmn/c7/cancel-bike-order.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.MISSING_SERVICE_TASK_IMPLEMENTATION).validate().assertNoErrors()
    }

    @Test
    fun `invalid bpmn detects violations`() {
        BpmnValidator
            .fromClasspath("bpmn/invalid-process.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.MISSING_SERVICE_TASK_IMPLEMENTATION, BpmnRules.MISSING_MESSAGE_NAME)
            .validate().assertHasViolations()
    }

    @Test
    fun `custom rules are applied via withRules`() {
        BpmnValidator
            .fromClasspath("bpmn/c7/cancel-bike-order.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.EMPTY_PROCESS).validate().assertNoViolations("empty-process")
    }

    @Test
    fun `disableRules filters out specified rules`() {
        BpmnValidator
            .fromClasspath("bpmn/invalid-process.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.MISSING_SERVICE_TASK_IMPLEMENTATION, BpmnRules.MISSING_MESSAGE_NAME)
            .disableRules("missing-service-task-implementation")
            .validate().assertNoViolations("missing-service-task-implementation")
    }

    @Test
    fun `disableRules switches off a mandatory rule since no code is generated`() {
        BpmnValidator
            .fromClasspath("bpmn/c7/cancel-bike-order.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(AlwaysFailingMandatoryRule())
            .disableRules("always-failing-mandatory").validate().assertNoViolations("always-failing-mandatory")
    }

    @Test
    fun `missing engine throws clear error`() {
        assertThatThrownBy {
            BpmnValidator.fromClasspath("bpmn/c7/cancel-bike-order.bpmn").validate()
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("Process engine must be set")
    }

    @Test
    fun `fromDirectory loads bpmn files`(@TempDir tempDir: Path) {
        // given: a BPMN file copied into a temp directory
        val bpmnContent = javaClass.classLoader.getResourceAsStream("bpmn/c7/cancel-bike-order.bpmn")!!
        Files.copy(bpmnContent, tempDir.resolve("test.bpmn"))

        // then: validation succeeds when loading from the directory
        BpmnValidator
            .fromDirectory(tempDir)
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.MISSING_SERVICE_TASK_IMPLEMENTATION).validate().assertNoErrors()
    }

    @Test
    fun `defaults to all rules when withRules is not called`() {
        BpmnValidator
            .fromClasspath("bpmn/c7/cancel-bike-order.bpmn").engine(ProcessEngine.CAMUNDA_7).validate().assertNoErrors()
    }

    @Test
    fun `failOnWarning promotes warnings to errors`() {
        val result = BpmnValidator
            .fromClasspath("bpmn/c7/cancel-bike-order.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(AlwaysViolatingRule("warn-rule", Severity.WARN)).failOnWarning().validate().result()

        assertThat(result.errors.map { it.ruleId }).contains("warn-rule")
        assertThat(result.warnings).isEmpty()
    }

    @Test
    fun `an error of one single-model rule does not keep the other single-model rules from reporting`() {
        val result = BpmnValidator
            .fromClasspath("bpmn/c7/cancel-bike-order.bpmn").engine(ProcessEngine.CAMUNDA_7).withRules(
                AlwaysViolatingRule(id = "first-error", severity = Severity.ERROR),
                AlwaysViolatingRule(id = "second-warn", severity = Severity.WARN),
            ).validate().result()

        assertThat(result.violations.map { it.ruleId }).contains("first-error", "second-warn")
    }

    @Test
    fun `validates files sharing a process id each on its own`(@TempDir tempDir: Path) {
        // given: the same process in two directories
        listOf("default", "corporate").forEach { directory ->
            val bpmnContent = javaClass.classLoader.getResourceAsStream("bpmn/c7/cancel-bike-order.bpmn")!!
            Files.copy(bpmnContent, Files.createDirectory(tempDir.resolve(directory)).resolve("cancel-bike-order.bpmn"))
        }

        // when: validating with the built-in rules and one that reports every model it sees
        val result = BpmnValidator
            .fromDirectory(tempDir).engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.all() + AlwaysViolatingRule(id = "seen", severity = Severity.WARN)).validate().result()

        // then: both files are validated, and sharing a process id is no violation
        assertThat(result.errors).isEmpty()
        assertThat(result.violations.filter { it.ruleId == "seen" }.map { it.processId }).containsExactly("cancelBikeOrder", "cancelBikeOrder")
    }

    private class AlwaysViolatingRule(override val id: String, override val severity: Severity) : SingleModelValidationRule {
        override fun validate(context: SingleModelValidationContext): List<ValidationViolation> = listOf(
            ValidationViolation(
                ruleId = id,
                severity = severity,
                elementId = null,
                processId = context.model.processId,
                message = "violation from $id",
            ),
        )
    }

    private class AlwaysFailingMandatoryRule : SingleModelValidationRule {
        override val id = "always-failing-mandatory"
        override val severity = Severity.ERROR
        override val mandatory = true

        override fun validate(context: SingleModelValidationContext): List<ValidationViolation> = listOf(
            ValidationViolation(
                ruleId = id,
                severity = severity,
                elementId = null,
                processId = context.model.processId,
                message = "always fails",
            ),
        )
    }
}
