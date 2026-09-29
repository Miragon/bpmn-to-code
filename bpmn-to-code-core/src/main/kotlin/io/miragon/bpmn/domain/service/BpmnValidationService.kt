package io.miragon.bpmn.domain.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.validation.BpmnValidationException
import io.miragon.bpmn.domain.validation.CrossModelValidationRule
import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.ValidationResult
import io.miragon.bpmn.domain.validation.ValidationRule
import io.miragon.bpmn.domain.validation.model.CrossModelValidationContext
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationConfig
import io.miragon.bpmn.domain.validation.model.ValidationPhase
import io.miragon.bpmn.domain.validation.model.ValidationViolation
import io.miragon.bpmn.domain.validation.rules.CollisionDetectionRule
import io.miragon.bpmn.domain.validation.rules.EmptyProcessRule
import io.miragon.bpmn.domain.validation.rules.EngineMismatchRule
import io.miragon.bpmn.domain.validation.rules.MissingCalledElementRule
import io.miragon.bpmn.domain.validation.rules.MissingElementIdRule
import io.miragon.bpmn.domain.validation.rules.MissingErrorDefinitionRule
import io.miragon.bpmn.domain.validation.rules.MissingMessageNameRule
import io.miragon.bpmn.domain.validation.rules.MissingProcessIdRule
import io.miragon.bpmn.domain.validation.rules.MissingServiceTaskImplementationRule
import io.miragon.bpmn.domain.validation.rules.MissingSignalNameRule
import io.miragon.bpmn.domain.validation.rules.MissingTimerDefinitionRule
import io.miragon.bpmn.domain.validation.rules.ReservedElementNameRule
import io.miragon.bpmn.domain.validation.rules.SharedDefinitionCollisionRule

class BpmnValidationService(
    private val config: ValidationConfig = ValidationConfig(),
    rules: List<ValidationRule> = builtInRules(),
) {

    private val logger = KotlinLogging.logger {}

    private val singleModelRules = rules.filterIsInstance<SingleModelValidationRule>()
    private val crossModelRules = rules.filterIsInstance<CrossModelValidationRule>()

    init {
        warnOnDisabledMandatoryRules()
    }

    fun collectViolations(models: List<ProcessModel>, engine: ProcessEngine, phase: ValidationPhase): List<ValidationViolation> {
        val activeSingleModelRules = singleModelRules
            .filter { it.phase == phase }.filterNot { it.id in config.disabledRules && !it.mandatory }

        val singleModelViolations = models.flatMap { model ->
            val ctx = SingleModelValidationContext(model, engine)
            activeSingleModelRules.flatMap { it.validate(ctx) }
        }
        return singleModelViolations + collectCrossModelViolations(models, engine, phase)
    }

    private fun collectCrossModelViolations(models: List<ProcessModel>, engine: ProcessEngine, phase: ValidationPhase): List<ValidationViolation> {
        if (phase != ValidationPhase.POST_MERGE) return emptyList()
        val ctx = CrossModelValidationContext(models, engine)
        return crossModelRules.filterNot { it.id in config.disabledRules && !it.mandatory }.flatMap { it.validate(ctx) }
    }

    fun validate(models: List<ProcessModel>, engine: ProcessEngine, phase: ValidationPhase) {
        val result = ValidationResult(collectViolations(models, engine, phase))
        result.warnings.forEach { logger.warn { it.describe() } }
        val failures = result.failures(config.failOnWarning)
        if (failures.isNotEmpty()) {
            throw BpmnValidationException(failures)
        }
    }

    /**
     * Mandatory rules are integrity-critical: they stay active on every path and cannot be disabled
     * via [ValidationConfig.disabledRules]. When a caller tries anyway, the attempt is surfaced as a
     * warning rather than silently ignored — the rule is kept active regardless.
     */
    private fun warnOnDisabledMandatoryRules() {
        (singleModelRules + crossModelRules).filter { it.mandatory && it.id in config.disabledRules }.forEach { rule ->
            logger.warn { "[BPMN VALIDATION] Rule '${rule.id}' is mandatory and cannot be disabled; keeping it active." }
        }
    }

    companion object {

        private fun builtInRules(): List<ValidationRule> = listOf(
            EngineMismatchRule(),
            MissingServiceTaskImplementationRule(),
            MissingMessageNameRule(),
            MissingErrorDefinitionRule(),
            MissingSignalNameRule(),
            MissingTimerDefinitionRule(),
            MissingCalledElementRule(),
            MissingElementIdRule(),
            EmptyProcessRule(),
            MissingProcessIdRule(),
            CollisionDetectionRule(),
            ReservedElementNameRule(),
            SharedDefinitionCollisionRule(),
        )
    }
}
