package io.miragon.bpmn.domain.shared

/**
 * The type of a BPMN flow node, modelled along two axes:
 *
 * - a structural *base type* ([Gateway], [Event], [Activity]) that captures the node's shape, and
 * - a concrete *subtype* that refines it (gateway kind, event shape, activity subtype).
 *
 * [Activity] mirrors the BPMN class hierarchy: an [Activity.Task] is itself an activity, alongside
 * the compound [Activity.SubProcess] and the [Activity.CallActivity] leaf. [Event] carries a second
 * sub-axis, [EventDefinitionType]. Invalid combinations (e.g. a timer gateway) are unrepresentable.
 */
sealed interface BpmnNodeType {

    data class Gateway(val kind: GatewayKind) : BpmnNodeType

    data class Event(
        val shape: EventShape,
        val definitionType: EventDefinitionType = EventDefinitionType.NONE,
    ) : BpmnNodeType

    /** BPMN activities: atomic [Task]s, compound [SubProcess]es, and the [CallActivity] leaf. */
    sealed interface Activity : BpmnNodeType {
        data class Task(val kind: TaskKind) : Activity
        data class SubProcess(val kind: SubProcessKind) : Activity
        object CallActivity : Activity
    }

    /** Fallback for element types not covered by this model, and the default for manually built nodes. */
    object Unknown : BpmnNodeType
}
