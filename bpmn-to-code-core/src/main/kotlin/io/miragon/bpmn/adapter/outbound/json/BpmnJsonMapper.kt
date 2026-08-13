package io.miragon.bpmn.adapter.outbound.json

import io.miragon.bpmn.adapter.outbound.json.model.CalledElementJson
import io.miragon.bpmn.adapter.outbound.json.model.DefinitionsJson
import io.miragon.bpmn.adapter.outbound.json.model.EventDefinitionJson
import io.miragon.bpmn.adapter.outbound.json.model.ExtensionJson
import io.miragon.bpmn.adapter.outbound.json.model.FlowNodeJson
import io.miragon.bpmn.adapter.outbound.json.model.ImplementationJson
import io.miragon.bpmn.adapter.outbound.json.model.IoMappingJson
import io.miragon.bpmn.adapter.outbound.json.model.MultiInstanceJson
import io.miragon.bpmn.adapter.outbound.json.model.ProcessJson
import io.miragon.bpmn.adapter.outbound.json.model.ProcessModelJson
import io.miragon.bpmn.adapter.outbound.json.model.SequenceFlowJson
import io.miragon.bpmn.adapter.outbound.json.model.VariableJson
import io.miragon.bpmn.adapter.outbound.json.model.VariantJson
import io.miragon.bpmn.adapter.outbound.shared.BpmnTypeName
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.EngineExtension
import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.IoMapping
import io.miragon.bpmn.domain.shared.MultiInstanceDefinition
import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.shared.RootElements
import io.miragon.bpmn.domain.shared.SequenceFlowDefinition
import io.miragon.bpmn.domain.shared.SubProcessKind
import io.miragon.bpmn.domain.shared.TaskImplementation
import io.miragon.bpmn.domain.shared.VariableDefinition
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Maps the domain model onto the public process-JSON contract (format 2.0, see ADR 018).
 *
 * Each scope is emitted with its own nodes and sequence flows, so nesting is structural rather than
 * inferred, and every node is sorted into process-flow order within its scope.
 */
@Suppress("TooManyFunctions")
internal class BpmnJsonMapper {

    fun toJson(model: ProcessModel): ProcessModelJson = ProcessModelJson(
        process = ProcessJson(
            id = model.processId,
            name = model.processName,
            isExecutable = model.isExecutable,
            engine = model.detectedEngine?.name,
            flowNodes = model.flowNodes.toJson(model.sequenceFlows),
            sequenceFlows = model.sequenceFlows.map { it.toJson() },
        ),
        definitions = model.definitions.toJson(),
        variants = model.variants.map { it.toJson() }.takeIf { it.isNotEmpty() },
    )

    private fun ProcessModel.Variant.toJson(): VariantJson = VariantJson(
        name = variantName,
        flowNodes = flowNodes.toJson(sequenceFlows),
        sequenceFlows = sequenceFlows.map { it.toJson() },
    )

    private fun RootElements.toJson(): DefinitionsJson = DefinitionsJson(
        messages = messages.mapNotNull { it.toJson() }.sortedBy { it.id },
        signals = signals.mapNotNull { it.toJson() }.sortedBy { it.id },
        errors = errors.mapNotNull { it.toJson() }.sortedBy { it.id },
        escalations = escalations.mapNotNull { it.toJson() }.sortedBy { it.id },
    )

    private fun List<FlowNodeDefinition>.toJson(sequenceFlows: List<SequenceFlowDefinition>): List<FlowNodeJson> = FlowNodeSorter.sort(this, sequenceFlows).map { it.toJson() }

    private fun FlowNodeDefinition.toJson(): FlowNodeJson {
        val activity = this as? FlowNodeDefinition.Activity
        val event = this as? FlowNodeDefinition.Event
        val subProcess = this as? FlowNodeDefinition.Activity.SubProcess
        return FlowNodeJson(
            id = id ?: "",
            type = BpmnTypeName.of(this),
            name = displayName,
            incoming = incoming,
            outgoing = outgoing,
            default = defaultFlow(),
            attachedToRef = event?.attachedToRef,
            cancelActivity = event?.takeIf { it.shape == EventShape.BOUNDARY_EVENT }?.interrupting,
            isInterrupting = event?.takeIf { it.shape == EventShape.START_EVENT }?.interrupting,
            triggeredByEvent = subProcess?.takeIf { it.kind == SubProcessKind.EVENT }?.let { true },
            isForCompensation = activity?.isForCompensation?.takeIf { it },
            boundaryEventRefs = activity?.boundaryEventRefs.orEmpty(),
            eventDefinitions = event?.eventDefinitions?.map { it.toJson() }.orEmpty(),
            messageRef = (this as? FlowNodeDefinition.Activity.Task)?.message?.messageRef,
            implementation = implementation()?.toJson(),
            calledElement = (this as? FlowNodeDefinition.Activity.CallActivity)?.toCalledElement(),
            multiInstance = activity?.multiInstance?.toJson(),
            ioMapping = ioMapping()?.toJson(),
            variables = variables.map { it.toJson() },
            flowNodes = subProcess?.flowNodes?.toJson(subProcess.sequenceFlows).orEmpty(),
            sequenceFlows = subProcess?.sequenceFlows?.map { it.toJson() }.orEmpty(),
            extensions = extensions.map { it.toJson() },
            engineAttributes = engineAttributes.mapValues { (_, value) -> value.toJsonElement() },
        )
    }

    private fun FlowNodeDefinition.defaultFlow(): String? = when (this) {
        is FlowNodeDefinition.Gateway -> defaultFlow
        is FlowNodeDefinition.Activity -> defaultFlow
        else -> null
    }

    private fun FlowNodeDefinition.implementation(): TaskImplementation? = when (this) {
        is FlowNodeDefinition.Activity.Task -> implementation
        is FlowNodeDefinition.Event -> implementation
        else -> null
    }

    private fun FlowNodeDefinition.ioMapping(): IoMapping? = when (this) {
        is FlowNodeDefinition.Activity -> ioMapping
        is FlowNodeDefinition.Event -> ioMapping
        else -> null
    }

    private fun FlowNodeDefinition.Activity.CallActivity.toCalledElement(): CalledElementJson? {
        val calledElement = CalledElementJson(
            processId = definition.getValue().takeIf { it.isNotEmpty() },
            propagateAllInputVariables = definition.propagateAllInputVariables,
            propagateAllOutputVariables = definition.propagateAllOutputVariables,
        )
        return calledElement.takeIf { it != CalledElementJson() }
    }

    private fun EventDefinitionInstance.toJson(): EventDefinitionJson = when (this) {
        is EventDefinitionInstance.Timer -> EventDefinitionJson.Timer(timerType?.name, expression)
        is EventDefinitionInstance.Message -> EventDefinitionJson.Message(reference.messageRef)
        is EventDefinitionInstance.Signal -> EventDefinitionJson.Signal(signalRef)
        is EventDefinitionInstance.Error -> EventDefinitionJson.Error(errorRef)
        is EventDefinitionInstance.Escalation -> EventDefinitionJson.Escalation(escalationRef)
        is EventDefinitionInstance.Compensation -> EventDefinitionJson.Compensation(activityRef, waitForCompletion)
        is EventDefinitionInstance.Conditional -> EventDefinitionJson.Conditional(expression)
        is EventDefinitionInstance.Link -> EventDefinitionJson.Link(linkName)
        is EventDefinitionInstance.Terminate -> EventDefinitionJson.Terminate
    }

    private fun TaskImplementation.toJson(): ImplementationJson? = when (this) {
        is TaskImplementation.Unspecified -> null
        is TaskImplementation.JobWorker -> ImplementationJson.JobWorker(jobType, retries)
        is TaskImplementation.Connector -> ImplementationJson.Connector(jobType, templateId, retries)
        is TaskImplementation.ExternalTask -> ImplementationJson.ExternalTask(topic)
        is TaskImplementation.JavaClass -> ImplementationJson.JavaClass(className)
        is TaskImplementation.DelegateExpression -> ImplementationJson.DelegateExpression(expression)
        is TaskImplementation.Expression -> ImplementationJson.Expression(expression)
    }

    private fun MultiInstanceDefinition.toJson(): MultiInstanceJson = MultiInstanceJson(
        sequential = sequential,
        inputCollection = inputCollection,
        inputElement = inputElement,
        outputCollection = outputCollection,
        outputElement = outputElement,
        cardinality = cardinality,
        completionCondition = completionCondition,
    )

    private fun IoMapping.toJson(): IoMappingJson = IoMappingJson(
        inputs = inputs.map { IoMappingJson.Parameter(it.target, it.source) },
        outputs = outputs.map { IoMappingJson.Parameter(it.target, it.source) },
    )

    private fun VariableDefinition.toJson(): VariableJson = VariableJson(name = getRawName(), direction = direction.name, expression = valueExpression)

    private fun EngineExtension.toJson(): ExtensionJson = ExtensionJson(
        type = type,
        attributes = attributes,
        children = children.map { it.toJson() },
        body = body,
    )

    private fun SequenceFlowDefinition.toJson(): SequenceFlowJson = SequenceFlowJson(
        id = id ?: "",
        sourceRef = sourceRef,
        targetRef = targetRef,
        name = flowName,
        conditionExpression = conditionExpression,
    )

    private fun RootElementDefinition.Message.toJson(): DefinitionsJson.Message? {
        val name = getValue().takeIf { it.isNotEmpty() } ?: return null
        return DefinitionsJson.Message(id = id ?: name, name = name, correlationKey = correlationKey)
    }

    private fun RootElementDefinition.Signal.toJson(): DefinitionsJson.Signal? {
        val name = getValue().takeIf { it.isNotEmpty() } ?: return null
        return DefinitionsJson.Signal(id = id ?: name, name = name)
    }

    private fun RootElementDefinition.Error.toJson(): DefinitionsJson.Error? {
        val (name, code) = getValue()
        if (name.isEmpty()) return null
        return DefinitionsJson.Error(id = id ?: name, name = name, errorCode = code.takeIf { it.isNotEmpty() })
    }

    private fun RootElementDefinition.Escalation.toJson(): DefinitionsJson.Escalation? {
        val (name, code) = getValue()
        if (name.isEmpty()) return null
        return DefinitionsJson.Escalation(id = id ?: name, name = name, escalationCode = code.takeIf { it.isNotEmpty() })
    }

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        else -> JsonPrimitive(this.toString())
    }
}
