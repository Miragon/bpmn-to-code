package io.miragon.bpmn.domain.validation

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
import io.miragon.bpmn.domain.validation.rules.UnreferencedRootElementRule

/**
 * The rules every entry point runs. [defaults] run on every generation and in the build task, and the testing library
 * starts from them too, adding [UNREFERENCED_ROOT_ELEMENT]. The [mandatory] ones run wherever validation runs.
 */
object BuiltInRules {

    val ENGINE_MISMATCH: SingleModelValidationRule = EngineMismatchRule()
    val MISSING_SERVICE_TASK_IMPLEMENTATION: SingleModelValidationRule = MissingServiceTaskImplementationRule()
    val MISSING_MESSAGE_NAME: SingleModelValidationRule = MissingMessageNameRule()
    val MISSING_ERROR_DEFINITION: SingleModelValidationRule = MissingErrorDefinitionRule()
    val MISSING_SIGNAL_NAME: SingleModelValidationRule = MissingSignalNameRule()
    val MISSING_TIMER_DEFINITION: SingleModelValidationRule = MissingTimerDefinitionRule()
    val MISSING_CALLED_ELEMENT: SingleModelValidationRule = MissingCalledElementRule()
    val MISSING_ELEMENT_ID: SingleModelValidationRule = MissingElementIdRule()
    val EMPTY_PROCESS: SingleModelValidationRule = EmptyProcessRule()
    val MISSING_PROCESS_ID: SingleModelValidationRule = MissingProcessIdRule()
    val COLLISION_DETECTION: SingleModelValidationRule = CollisionDetectionRule()
    val RESERVED_ELEMENT_NAME: SingleModelValidationRule = ReservedElementNameRule()
    val SHARED_DEFINITION_COLLISION: CrossModelValidationRule = SharedDefinitionCollisionRule()
    val UNREFERENCED_ROOT_ELEMENT: SingleModelValidationRule = UnreferencedRootElementRule()

    fun defaults(): List<ValidationRule> = listOf(
        ENGINE_MISMATCH,
        MISSING_SERVICE_TASK_IMPLEMENTATION,
        MISSING_MESSAGE_NAME,
        MISSING_ERROR_DEFINITION,
        MISSING_SIGNAL_NAME,
        MISSING_TIMER_DEFINITION,
        MISSING_CALLED_ELEMENT,
        MISSING_ELEMENT_ID,
        EMPTY_PROCESS,
        MISSING_PROCESS_ID,
        COLLISION_DETECTION,
        RESERVED_ELEMENT_NAME,
        SHARED_DEFINITION_COLLISION,
    )

    fun mandatory(): List<ValidationRule> = defaults().filter { it.mandatory }
}
