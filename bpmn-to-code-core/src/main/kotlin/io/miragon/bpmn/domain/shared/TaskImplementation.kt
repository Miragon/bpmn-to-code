package io.miragon.bpmn.domain.shared

/**
 * How a service-task-like node is implemented, normalised across engines.
 *
 * Per `camunda:ServiceTaskLike` (and its Zeebe equivalent) this applies to service tasks, business-rule
 * tasks, send tasks **and** message event definitions, which is why it is carried by both
 * [FlowNodeDefinition.Activity.Task] and [FlowNodeDefinition.Event].
 *
 * [reference] is the single string a consumer subscribes to or dispatches on — the Zeebe job type, the
 * Camunda 7 topic, the delegate expression, … It is `null` only for [Unspecified], i.e. a node that is
 * service-task-like but carries no implementation configuration at all.
 */
sealed interface TaskImplementation {

    val reference: String?

    /**
     * Declared as service-task-like, but nothing is configured. Flagged by the validation rules.
     */
    data object Unspecified : TaskImplementation {
        override val reference: String? = null
    }

    /**
     * Zeebe `zeebe:taskDefinition` handled by a job worker.
     */
    data class JobWorker(val jobType: String, val retries: String? = null) : TaskImplementation {
        override val reference: String get() = jobType
    }

    /**
     * Zeebe `zeebe:taskDefinition` backed by an element template (outbound connector).
     */
    data class Connector(val jobType: String, val templateId: String? = null, val retries: String? = null) : TaskImplementation {
        override val reference: String get() = jobType
    }

    /**
     * Camunda 7 / Operaton `camunda:topic` handled by an external task worker.
     */
    data class ExternalTask(val topic: String) : TaskImplementation {
        override val reference: String get() = topic
    }

    /**
     * Camunda 7 / Operaton `camunda:class`.
     */
    data class JavaClass(val className: String) : TaskImplementation {
        override val reference: String get() = className
    }

    /**
     * Camunda 7 / Operaton `camunda:delegateExpression`.
     */
    data class DelegateExpression(val expression: String) : TaskImplementation {
        override val reference: String get() = expression
    }

    /**
     * Camunda 7 / Operaton `camunda:expression`.
     */
    data class Expression(val expression: String) : TaskImplementation {
        override val reference: String get() = expression
    }
}
