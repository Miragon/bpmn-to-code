package io.miragon.bpmn.adapter.outbound.json.model

import kotlinx.serialization.Serializable

/**
 * The `bpmn:Definitions` root elements referenced by the process, de-duplicated by their own id.
 *
 * Nodes point here through their `…Ref` members, so a message used by three events is one entry — the
 * reason these are entities rather than copies on the node tree (ADR 018).
 */
@Serializable
internal data class DefinitionsJson(
    val messages: List<Message> = emptyList(),
    val signals: List<Signal> = emptyList(),
    val errors: List<Error> = emptyList(),
    val escalations: List<Escalation> = emptyList(),
) {

    @Serializable
    data class Message(val id: String, val name: String, val correlationKey: String? = null)

    @Serializable
    data class Signal(val id: String, val name: String)

    @Serializable
    data class Error(val id: String, val name: String, val errorCode: String? = null)

    @Serializable
    data class Escalation(val id: String, val name: String, val escalationCode: String? = null)
}
