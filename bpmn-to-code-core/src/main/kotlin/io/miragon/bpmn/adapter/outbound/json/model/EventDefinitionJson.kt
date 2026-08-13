package io.miragon.bpmn.adapter.outbound.json.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * One `bpmn:*EventDefinition` on an event node, discriminated by `type`. BPMN allows several definitions
 * on one event, so a node carries a list of these rather than a single kind.
 *
 * The `…Ref` members point into [DefinitionsJson]; resolve them there for the name and code.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
internal sealed interface EventDefinitionJson {

    @Serializable
    @SerialName("timer")
    data class Timer(val timerType: String? = null, val expression: String? = null) : EventDefinitionJson

    @Serializable
    @SerialName("message")
    data class Message(val messageRef: String? = null) : EventDefinitionJson

    @Serializable
    @SerialName("signal")
    data class Signal(val signalRef: String? = null) : EventDefinitionJson

    @Serializable
    @SerialName("error")
    data class Error(val errorRef: String? = null) : EventDefinitionJson

    @Serializable
    @SerialName("escalation")
    data class Escalation(val escalationRef: String? = null) : EventDefinitionJson

    @Serializable
    @SerialName("compensation")
    data class Compensation(val activityRef: String? = null, val waitForCompletion: Boolean? = null) : EventDefinitionJson

    @Serializable
    @SerialName("conditional")
    data class Conditional(val expression: String? = null) : EventDefinitionJson

    @Serializable
    @SerialName("link")
    data class Link(val linkName: String? = null) : EventDefinitionJson

    @Serializable
    @SerialName("terminate")
    data object Terminate : EventDefinitionJson
}
