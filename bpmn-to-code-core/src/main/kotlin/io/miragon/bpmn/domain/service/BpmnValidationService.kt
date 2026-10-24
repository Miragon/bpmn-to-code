package io.miragon.bpmn.domain.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.SourcedProcessModel
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.validation.BpmnValidationException
import io.miragon.bpmn.domain.validation.BuiltInRules
import io.miragon.bpmn.domain.validation.CrossModelValidationRule
import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.ValidationResult
import io.miragon.bpmn.domain.validation.ValidationRule
import io.miragon.bpmn.domain.validation.model.CrossModelValidationContext
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationConfig
import io.miragon.bpmn.domain.validation.model.ValidationViolation

/**
 * The one validation every entry point runs. Each model is validated on its own, and the models against each other
 * once none of them has an error. Generation and the build task check the same: the processes that get a Process API
 * pass the rules, and no two files would be generated under one name. The mandatory rules run whatever [rules] lists.
 */
class BpmnValidationService(
    private val config: ValidationConfig = ValidationConfig(),
    rules: List<ValidationRule> = BuiltInRules.defaults(),
) {

    private val logger = KotlinLogging.logger {}

    private val rulesToRun = (rules + BuiltInRules.mandatory()).distinctBy { it.id }

    private val singleModelRules = rulesToRun.filterIsInstance<SingleModelValidationRule>().filter { it.isActive() }
    private val crossModelRules = rulesToRun.filterIsInstance<CrossModelValidationRule>().filter { it.isActive() }

    init {
        warnOnDisabledMandatoryRules(rulesToRun)
    }

    fun validate(models: List<ProcessModel>, engine: ProcessEngine): ValidationResult {
        val singleModelViolations = models.flatMap { model ->
            val context = SingleModelValidationContext(model, engine)
            singleModelRules.flatMap { it.validate(context) }
        }
        if (singleModelViolations.any { it.severity == Severity.ERROR }) {
            return ValidationResult(singleModelViolations)
        }
        val context = CrossModelValidationContext(models, engine)
        val crossModelViolations = crossModelRules.flatMap { it.validate(context) }
        return ValidationResult(singleModelViolations + crossModelViolations)
    }

    /**
     * The processes a Process API is generated for, normalized and in the order they are generated in. Warnings are
     * logged; the build task calls this too, so it fails where the generation fails.
     *
     * @throws BpmnValidationException when a rule fails
     * @throws io.miragon.bpmn.domain.ProcessApiNamingException when the models pass but two files would be generated
     * under one name
     */
    fun processApiSources(sources: List<SourcedProcessModel>, engine: ProcessEngine): List<SourcedProcessModel> {
        val executableSources = SourcedProcessModel.executableOnly(sources)
        failOn(validateSources(sources = executableSources, engine = engine))
        return normalized(executableSources)
    }

    private fun validateSources(sources: List<SourcedProcessModel>, engine: ProcessEngine): ValidationResult {
        val result = validate(models = sources.map { it.model }, engine = engine)
        if (!result.hasFailures(config.failOnWarning)) {
            SourcedProcessModel.requireDistinctApiNames(sources)
        }
        return result
    }

    private fun normalized(sources: List<SourcedProcessModel>): List<SourcedProcessModel> {
        val normalizedSources = sources.map { it.copy(model = it.model.normalized()) }
        return normalizedSources.sortedBy { it.model.apiName }
    }

    private fun failOn(result: ValidationResult) {
        result.warnings.forEach { logger.warn { it.describe() } }
        val failures: List<ValidationViolation> = result.failures(config.failOnWarning)
        if (failures.isNotEmpty()) {
            throw BpmnValidationException(failures)
        }
    }

    private fun ValidationRule.isActive(): Boolean = mandatory || id !in config.disabledRules

    /**
     * Mandatory rules are integrity-critical: they stay active on every path and cannot be disabled
     * via [ValidationConfig.disabledRules]. When a caller tries anyway, the attempt is surfaced as a
     * warning rather than silently ignored — the rule is kept active regardless.
     */
    private fun warnOnDisabledMandatoryRules(rules: List<ValidationRule>) {
        rules.filter { it.mandatory && it.id in config.disabledRules }.forEach { rule ->
            logger.warn { "[BPMN VALIDATION] Rule '${rule.id}' is mandatory and cannot be disabled; keeping it active." }
        }
    }
}
