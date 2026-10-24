package io.miragon.bpmn.testing

import io.miragon.bpmn.adapter.inbound.ExtractProcessModelsPlugin
import io.miragon.bpmn.domain.BpmnResource
import io.miragon.bpmn.domain.service.BpmnValidationService
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.validation.CrossModelValidationRule
import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.ValidationResult
import io.miragon.bpmn.domain.validation.ValidationRule
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.ValidationConfig
import java.nio.file.Path

/**
 * Fluent entry point for validating BPMN process models in tests.
 *
 * Example usage:
 * ```kotlin
 * BpmnValidator
 *     .fromClasspath("bpmn/")
 *     .engine(ProcessEngine.CAMUNDA_7)
 *     .validate()
 *     .assertNoViolations()
 * ```
 */
class BpmnValidator private constructor(private val resourceLoader: () -> List<BpmnResource>) {

    private var engine: ProcessEngine? = null
    private var rules: List<ValidationRule>? = null
    private var disabledRuleIds: Set<String> = emptySet()
    private var failOnWarning: Boolean = false

    /**
     * Sets the process engine used to parse BPMN files.
     */
    fun engine(engine: ProcessEngine): BpmnValidator {
        this.engine = engine
        return this
    }

    /**
     * Sets the validation rules to use. Accepts both [SingleModelValidationRule] and
     * [CrossModelValidationRule]. Defaults to [BpmnRules.all] if not called. The mandatory rules run in any case.
     */
    fun withRules(vararg rules: ValidationRule): BpmnValidator {
        this.rules = rules.toList()
        return this
    }

    /**
     * Sets the validation rules to use. Accepts both [SingleModelValidationRule] and
     * [CrossModelValidationRule]. Defaults to [BpmnRules.all] if not called. The mandatory rules run in any case.
     */
    fun withRules(rules: List<ValidationRule>): BpmnValidator {
        this.rules = rules
        return this
    }

    /**
     * Disables the given rules by their IDs. Mandatory rules stay active.
     */
    fun disableRules(vararg ruleIds: String): BpmnValidator {
        this.disabledRuleIds = ruleIds.toSet()
        return this
    }

    /**
     * Treats warnings as failures during validation.
     */
    fun failOnWarning(): BpmnValidator {
        this.failOnWarning = true
        return this
    }

    /**
     * Executes validation and returns a [BpmnValidationAssert] for fluent assertions.
     */
    fun validate(): BpmnValidationAssert {
        val selectedEngine = requireNotNull(engine) {
            "Process engine must be set. Call .engine(ProcessEngine.CAMUNDA_7) or similar before .validate()"
        }

        val models = ExtractProcessModelsPlugin().execute(resourceLoader(), selectedEngine)
        val validationService = BpmnValidationService(config = ValidationConfig(disabledRules = disabledRuleIds), rules = rules ?: BpmnRules.all())
        val result = validationService.validate(models = models, engine = selectedEngine)
        return BpmnValidationAssert.assertThat(applyPolicy(result))
    }

    private fun applyPolicy(result: ValidationResult): ValidationResult {
        if (!failOnWarning) return result
        return ValidationResult(result.violations.map { if (it.severity == Severity.WARN) it.copy(severity = Severity.ERROR) else it })
    }

    companion object {

        /**
         * Loads BPMN files from the classpath at the given path.
         */
        @JvmStatic
        fun fromClasspath(path: String): BpmnValidator = BpmnValidator { BpmnResourceLoader.fromClasspath(path) }

        /**
         * Loads BPMN files from a filesystem directory.
         */
        @JvmStatic
        fun fromDirectory(directory: Path): BpmnValidator = BpmnValidator { BpmnResourceLoader.fromDirectory(directory) }
    }
}
