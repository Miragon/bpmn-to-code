package io.miragon.bpmn.adapter.outbound.json

import io.miragon.bpmn.adapter.outbound.json.model.BpmnModelJson
import io.miragon.bpmn.adapter.outbound.json.model.CompensationJson
import io.miragon.bpmn.adapter.outbound.json.model.EscalationJson
import io.miragon.bpmn.adapter.outbound.json.model.ErrorJson
import io.miragon.bpmn.adapter.outbound.json.model.FlowNodeJson
import io.miragon.bpmn.adapter.outbound.json.model.FlowNodePropertiesJson
import io.miragon.bpmn.adapter.outbound.json.model.MessageJson
import io.miragon.bpmn.adapter.outbound.json.model.SequenceFlowJson
import io.miragon.bpmn.adapter.outbound.json.model.SignalJson
import io.miragon.bpmn.adapter.outbound.json.model.VariantJson
import io.miragon.bpmn.adapter.outbound.shared.ElementTypeName
import io.miragon.bpmn.domain.BpmnModel
import io.miragon.bpmn.domain.MergedBpmnModel
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.CompensationDefinition
import io.miragon.bpmn.domain.shared.ErrorDefinition
import io.miragon.bpmn.domain.shared.EscalationDefinition
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.FlowNodeProperties
import io.miragon.bpmn.domain.shared.MessageDefinition
import io.miragon.bpmn.domain.shared.SequenceFlowDefinition
import io.miragon.bpmn.domain.shared.ServiceTaskDefinition
import io.miragon.bpmn.domain.shared.SignalDefinition
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

class BpmnJsonMapper {

    fun toJson(model: ProcessModel): BpmnModelJson {
        return when (model) {
            is BpmnModel -> toFlatJson(model)
            is MergedBpmnModel -> toVariantJson(model)
        }
    }

    private fun toFlatJson(model: BpmnModel): BpmnModelJson {
        val messageEngineProperties = model.messages.messageEnginePropertiesByNode()
        return BpmnModelJson(
            processId = model.processId,
            flowNodes = FlowNodeSorter.sort(model.flowNodes).map { it.toJson(messageEngineProperties) },
            sequenceFlows = model.sequenceFlows.map { it.toJson() },
            messages = model.messages.mapNotNull { it.toJson() },
            signals = model.signals.mapNotNull { it.toJson() },
            errors = model.errors.mapNotNull { it.toJson() },
            escalations = model.escalations.mapNotNull { it.toJson() },
            compensations = model.compensations.mapNotNull { it.toJson() },
        )
    }

    private fun toVariantJson(model: MergedBpmnModel): BpmnModelJson {
        val messageEngineProperties = model.messages.messageEnginePropertiesByNode()
        return BpmnModelJson(
            processId = model.processId,
            messages = model.messages.mapNotNull { it.toJson() },
            signals = model.signals.mapNotNull { it.toJson() },
            errors = model.errors.mapNotNull { it.toJson() },
            escalations = model.escalations.mapNotNull { it.toJson() },
            compensations = model.compensations.mapNotNull { it.toJson() },
            variants = model.variants.map { variant ->
                VariantJson(
                    variantName = variant.variantName,
                    flowNodes = FlowNodeSorter.sort(variant.flowNodes).map { it.toJson(messageEngineProperties) },
                    sequenceFlows = variant.sequenceFlows.map { it.toJson() },
                )
            },
        )
    }

    private fun List<MessageDefinition>.messageEnginePropertiesByNode(): Map<String?, Map<String, Any?>> {
        return associate { it.id to it.engineSpecificProperties }
    }

    private fun FlowNodeDefinition.toJson(messageEngineProperties: Map<String?, Map<String, Any?>>): FlowNodeJson {
        return FlowNodeJson(
            id = id ?: "",
            displayName = displayName,
            elementType = ElementTypeName.of(nodeType),
            parentId = parentId,
            attachedToRef = attachedToRef,
            interrupting = interrupting,
            attachedElements = attachedElements,
            previousElements = previousElements,
            followingElements = followingElements,
            variables = variables.map { it.getRawName() },
            properties = properties.toJson(messageEngineProperties[id].orEmpty()),
            engineSpecificProperties = engineSpecificProperties.mapValues { (_, v) -> v.toJsonElement() },
        )
    }

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        else -> JsonPrimitive(this.toString())
    }

    private fun FlowNodeProperties.toJson(messageEngineProperties: Map<String, Any?>): FlowNodePropertiesJson? = when (this) {
        is FlowNodeProperties.None -> null
        is FlowNodeProperties.ServiceTask -> FlowNodePropertiesJson(
            type = "ServiceTask",
            implementationValue = definition.engineSpecificProperties[ServiceTaskDefinition.IMPL_VALUE_KEY] as? String,
            implementationKind = definition.engineSpecificProperties[ServiceTaskDefinition.IMPL_KIND_KEY] as? String,
        )
        is FlowNodeProperties.CallActivity -> FlowNodePropertiesJson(
            type = "CallActivity",
            calledElement = definition.getValue(),
        )
        is FlowNodeProperties.Timer -> {
            val (type, value) = definition.getValue()
            FlowNodePropertiesJson(
                type = "Timer",
                timerType = type.takeIf { it.isNotEmpty() },
                timerValue = value.takeIf { it.isNotEmpty() },
            )
        }
        is FlowNodeProperties.MessageEvent -> FlowNodePropertiesJson(
            type = "MessageEvent",
            messageName = name,
            messageDirection = direction.name,
            engineSpecificProperties = messageEngineProperties.mapValues { (_, v) -> v.toJsonElement() },
        )
        is FlowNodeProperties.SignalEvent -> FlowNodePropertiesJson(
            type = "SignalEvent",
            signalName = name,
            signalDirection = direction.name,
        )
    }

    private fun SequenceFlowDefinition.toJson(): SequenceFlowJson {
        return SequenceFlowJson(
            id = id ?: "",
            sourceRef = sourceRef,
            targetRef = targetRef,
            name = flowName,
            conditionExpression = conditionExpression,
            isDefault = isDefault,
        )
    }

    private fun MessageDefinition.toJson(): MessageJson? {
        val name = getValue().takeIf { it.isNotEmpty() } ?: return null
        return MessageJson(id = id ?: "", name = name)
    }

    private fun SignalDefinition.toJson(): SignalJson? {
        val name = getValue().takeIf { it.isNotEmpty() } ?: return null
        return SignalJson(id = id ?: "", name = name)
    }

    private fun ErrorDefinition.toJson(): ErrorJson? {
        val (name, code) = getValue()
        if (name.isEmpty()) return null
        return ErrorJson(id = id ?: "", name = name, code = code)
    }

    private fun EscalationDefinition.toJson(): EscalationJson? {
        val (name, code) = getValue()
        if (name.isEmpty()) return null
        return EscalationJson(id = id ?: "", name = name, code = code)
    }

    private fun CompensationDefinition.toJson(): CompensationJson? {
        val activityRef = getValue().takeIf { it.isNotEmpty() } ?: return null
        return CompensationJson(id = id ?: "", activityRef = activityRef)
    }
}
