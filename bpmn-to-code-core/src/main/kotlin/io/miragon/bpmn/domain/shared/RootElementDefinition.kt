package io.miragon.bpmn.domain.shared

import io.miragon.bpmn.domain.utils.StringUtils.toUpperSnakeCase

/**
 * A `bpmn:Definitions` root element — a message, signal, error or escalation.
 *
 * Flow nodes reference these by [id], so registries are keyed by id rather than by name: BPMN permits two
 * root elements with the same name and distinct ids (a modeller typing the same message name twice instead
 * of picking the existing one), and both have to stay resolvable. Collapsing them to one generated constant
 * is a code-generation concern and happens there.
 *
 * The four are grouped as a [RootElements] registry on the process model.
 */
sealed interface RootElementDefinition {

    val id: String?

    /**
     * A `bpmn:Message` root element. [correlationKey] is the Zeebe `zeebe:subscription` expression, which
     * is declared on the message element itself and so belongs here rather than on each referencing node.
     */
    data class Message(
        override val id: String?,
        private val name: String?,
        val correlationKey: String? = null,
    ) : RootElementDefinition,
        VariableMapping<String> {
        override fun getName() = name?.toUpperSnakeCase() ?: ""
        override fun getValue() = name ?: ""
        override fun getRawName() = name ?: ""
    }

    /**
     * A `bpmn:Signal` root element, referenced by every signal event definition that publishes or catches
     * it.
     *
     * [hasName] has no counterpart on [Message] on purpose: `MissingSignalNameRule` checks the registry,
     * while `MissingMessageNameRule` checks the reference on the node.
     */
    data class Signal(
        override val id: String?,
        private val name: String?,
    ) : RootElementDefinition,
        VariableMapping<String> {
        override fun getName() = name?.toUpperSnakeCase() ?: ""
        override fun getValue() = name ?: ""
        override fun getRawName() = name ?: ""
        fun hasName() = name != null
    }

    /**
     * A `bpmn:Error` root element, referenced by error event definitions via `errorRef`.
     */
    data class Error(
        override val id: String?,
        private val name: String?,
        private val code: String?,
    ) : RootElementDefinition,
        VariableMapping<Pair<String, String>> {
        override fun getName() = name?.toUpperSnakeCase() ?: ""
        override fun getValue() = (name ?: "") to (code ?: "")
        override fun getRawName() = name ?: ""
    }

    /**
     * A `bpmn:Escalation` root element, referenced by escalation event definitions via `escalationRef`.
     */
    data class Escalation(
        override val id: String?,
        private val name: String?,
        private val code: String?,
    ) : RootElementDefinition,
        VariableMapping<Pair<String, String>> {
        override fun getName() = name?.toUpperSnakeCase() ?: ""
        override fun getValue() = (name ?: "") to (code ?: "")
        override fun getRawName() = name ?: ""
    }
}
