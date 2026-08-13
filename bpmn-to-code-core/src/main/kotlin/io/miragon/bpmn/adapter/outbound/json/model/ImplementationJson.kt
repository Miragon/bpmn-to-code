package io.miragon.bpmn.adapter.outbound.json.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * How a service-task-like node is implemented, normalised across engines and discriminated by `type`.
 * Absent when the node declares no implementation at all.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
internal sealed interface ImplementationJson {

    @Serializable
    @SerialName("jobWorker")
    data class JobWorker(val jobType: String, val retries: String? = null) : ImplementationJson

    @Serializable
    @SerialName("connector")
    data class Connector(val jobType: String, val templateId: String? = null, val retries: String? = null) : ImplementationJson

    @Serializable
    @SerialName("externalTask")
    data class ExternalTask(val topic: String) : ImplementationJson

    @Serializable
    @SerialName("javaClass")
    data class JavaClass(val className: String) : ImplementationJson

    @Serializable
    @SerialName("delegateExpression")
    data class DelegateExpression(val expression: String) : ImplementationJson

    @Serializable
    @SerialName("expression")
    data class Expression(val expression: String) : ImplementationJson
}
