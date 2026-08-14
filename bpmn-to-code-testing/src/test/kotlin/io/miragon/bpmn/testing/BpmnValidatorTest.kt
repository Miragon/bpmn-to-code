package io.miragon.bpmn.testing

import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationPhase
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
            .fromClasspath("bpmn/valid-process.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.MISSING_SERVICE_TASK_IMPLEMENTATION)
            .validate()
            .assertNoErrors()
    }

    @Test
    fun `invalid bpmn detects violations`() {
        BpmnValidator
            .fromClasspath("bpmn/invalid-process.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.MISSING_SERVICE_TASK_IMPLEMENTATION, BpmnRules.MISSING_MESSAGE_NAME)
            .validate()
            .assertHasViolations()
    }

    @Test
    fun `custom rules are applied via withRules`() {
        BpmnValidator
            .fromClasspath("bpmn/valid-process.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.EMPTY_PROCESS)
            .validate()
            .assertNoViolations("empty-process")
    }

    @Test
    fun `disableRules filters out specified rules`() {
        BpmnValidator
            .fromClasspath("bpmn/invalid-process.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.MISSING_SERVICE_TASK_IMPLEMENTATION, BpmnRules.MISSING_MESSAGE_NAME)
            .disableRules("missing-service-task-implementation")
            .validate()
            .assertNoViolations("missing-service-task-implementation")
    }

    @Test
    fun `disableRules switches off a mandatory rule since no code is generated`() {
        BpmnValidator
            .fromClasspath("bpmn/valid-process.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(AlwaysFailingMandatoryRule())
            .disableRules("always-failing-mandatory")
            .validate()
            .assertNoViolations("always-failing-mandatory")
    }

    @Test
    fun `missing engine throws clear error`() {
        assertThatThrownBy {
            BpmnValidator
                .fromClasspath("bpmn/valid-process.bpmn")
                .validate()
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Process engine must be set")
    }

    @Test
    fun `fromDirectory loads bpmn files`(@TempDir tempDir: Path) {
        // given: a BPMN file copied into a temp directory
        val bpmnContent = javaClass.classLoader.getResourceAsStream("bpmn/valid-process.bpmn")!!
        Files.copy(bpmnContent, tempDir.resolve("test.bpmn"))

        // then: validation succeeds when loading from the directory
        BpmnValidator
            .fromDirectory(tempDir)
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.MISSING_SERVICE_TASK_IMPLEMENTATION)
            .validate()
            .assertNoErrors()
    }

    @Test
    fun `defaults to all rules when withRules is not called`() {
        BpmnValidator
            .fromClasspath("bpmn/valid-process.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .validate()
            .assertNoErrors()
    }

    @Test
    fun `failOnWarning promotes warnings to errors`() {
        val result = BpmnValidator
            .fromClasspath("bpmn/valid-process.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(AlwaysViolatingRule("warn-rule", Severity.WARN))
            .failOnWarning()
            .validate()
            .result()

        assertThat(result.errors.map { it.ruleId }).contains("warn-rule")
        assertThat(result.warnings).isEmpty()
    }

    @Test
    fun `warnings in the pre-merge phase do not short-circuit post-merge rules`() {
        val result = BpmnValidator
            .fromClasspath("bpmn/valid-process.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(
                AlwaysViolatingRule("pre-warn", Severity.WARN, ValidationPhase.PRE_MERGE),
                AlwaysViolatingRule("post-warn", Severity.WARN, ValidationPhase.POST_MERGE),
            )
            .validate()
            .result()

        assertThat(result.violations.map { it.ruleId }).contains("pre-warn", "post-warn")
    }

    @Test
    fun `an error in the pre-merge phase short-circuits post-merge rules`() {
        val result = BpmnValidator
            .fromClasspath("bpmn/valid-process.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(
                AlwaysViolatingRule("pre-error", Severity.ERROR, ValidationPhase.PRE_MERGE),
                AlwaysViolatingRule("post-warn", Severity.WARN, ValidationPhase.POST_MERGE),
            )
            .validate()
            .result()

        assertThat(result.violations.map { it.ruleId }).contains("pre-error")
        assertThat(result.violations.map { it.ruleId }).doesNotContain("post-warn")
    }

    private class AlwaysViolatingRule(
        override val id: String,
        override val severity: Severity,
        override val phase: ValidationPhase = ValidationPhase.PRE_MERGE,
    ) : SingleModelValidationRule {
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
