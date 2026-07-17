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

@Deprecated(
    message = "Renamed to SingleModelValidationContext",
    replaceWith = ReplaceWith("SingleModelValidationContext"),
)
typealias ValidationContext = SingleModelValidationContext
