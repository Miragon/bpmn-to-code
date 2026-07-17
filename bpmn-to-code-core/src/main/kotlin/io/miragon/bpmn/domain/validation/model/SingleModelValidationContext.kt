package io.miragon.bpmn.domain.validation.model

import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.ProcessEngine

/**
 * Context handed to a [io.miragon.bpmn.domain.validation.SingleModelValidationRule]. Carries the single
 * process model under validation. For rules that reason across processes, see
 * [io.miragon.bpmn.domain.validation.model.CrossModelValidationContext].
 */
data class SingleModelValidationContext(
    val model: ProcessModel,
    val engine: ProcessEngine,
)

/*
 * Backward-compatibility alias for the previous type name. Kept as a temporary migration aid;
 * scheduled for removal in v6.0.0. Migrate to [SingleModelValidationContext].
 */
@Deprecated(
    message = "Renamed to SingleModelValidationContext; this alias will be removed in v6.0.0",
    replaceWith = ReplaceWith("SingleModelValidationContext"),
)
typealias ValidationContext = SingleModelValidationContext
