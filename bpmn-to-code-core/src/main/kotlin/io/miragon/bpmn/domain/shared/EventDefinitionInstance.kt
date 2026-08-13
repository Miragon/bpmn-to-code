package io.miragon.bpmn.domain.shared

/**
 * One `bpmn:*EventDefinition` carried by an event node.
 *
 * BPMN models this as a **list** (`bpmn:CatchEvent.eventDefinitions`) — an event may carry several
 * triggers — so [FlowNodeDefinition.Event] holds a list of these rather than a single kind.
 *
 * The `…Ref` fields point at the corresponding `bpmn:Definitions` root element (see [RootElementDefinition.Message],
 * [RootElementDefinition.Signal], [RootElementDefinition.Error], [RootElementDefinition.Escalation]); the redundant name is kept alongside so
 * validation rules can correlate without resolving the registry.
 */
sealed interface EventDefinitionInstance {

    val type: EventDefinitionInstance.Type

    data class Timer(
        val timerType: TimerType? = null,
        val expression: String? = null,
    ) : EventDefinitionInstance {
        override val type = EventDefinitionInstance.Type.TIMER
    }

    data class Message(
        val reference: MessageReference,
    ) : EventDefinitionInstance {
        override val type = EventDefinitionInstance.Type.MESSAGE
    }

    data class Signal(
        val signalRef: String? = null,
        val signalName: String? = null,
    ) : EventDefinitionInstance {
        override val type = EventDefinitionInstance.Type.SIGNAL
    }

    data class Error(
        val errorRef: String? = null,
        val errorName: String? = null,
        val errorCode: String? = null,
    ) : EventDefinitionInstance {
        override val type = EventDefinitionInstance.Type.ERROR
    }

    data class Escalation(
        val escalationRef: String? = null,
        val escalationName: String? = null,
        val escalationCode: String? = null,
    ) : EventDefinitionInstance {
        override val type = EventDefinitionInstance.Type.ESCALATION
    }

    data class Compensation(
        val activityRef: String? = null,
        val waitForCompletion: Boolean? = null,
    ) : EventDefinitionInstance {
        override val type = EventDefinitionInstance.Type.COMPENSATION
    }

    data class Conditional(
        val expression: String? = null,
    ) : EventDefinitionInstance {
        override val type = EventDefinitionInstance.Type.CONDITIONAL
    }

    data class Link(
        val linkName: String? = null,
    ) : EventDefinitionInstance {
        override val type = EventDefinitionInstance.Type.LINK
    }

    data object Terminate : EventDefinitionInstance {
        override val type = EventDefinitionInstance.Type.TERMINATE
    }

    /**
     * The kind of BPMN event definition carried by an [EventDefinitionInstance].
     *
     * Per the BPMN 2.0 (OMG) spec an event definition acts as a *trigger* on catching events
     * (start, intermediate-catch, boundary) and describes a *result* on throwing events
     * (intermediate-throw, end). This enum captures that shared definition kind for both roles.
     *
     * An event with no definition at all is represented by an empty
     * [FlowNodeDefinition.Event.eventDefinitions] list, not by a member of this enum.
     */
    enum class Type {
        TIMER,
        MESSAGE,
        ERROR,
        SIGNAL,
        ESCALATION,
        COMPENSATION,
        CONDITIONAL,
        LINK,
        TERMINATE,
    }
}
