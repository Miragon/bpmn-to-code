package io.miragon.bpmn.adapter.outbound.codegen.flow

import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.MappingFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.NamedCode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.NodeFacets
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedConstant
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedValue
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.TimerFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.VariableFacet
import io.miragon.bpmn.domain.shared.CallActivityDefinition
import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.MessageReference
import io.miragon.bpmn.domain.shared.RootElements
import io.miragon.bpmn.domain.shared.ServiceTaskDefinition
import io.miragon.bpmn.domain.shared.VariableDefinition
import io.miragon.bpmn.domain.shared.VariableMapping
import io.miragon.bpmn.domain.utils.StringUtils.toUpperSnakeCase

/**
 * Collects a node's own data into [NodeFacets], mirroring the sealed [FlowNodeDefinition] hierarchy.
 *
 * Event references (message, signal, error, escalation) are resolved through the model's root-element
 * [RootElements] by ref first, so a node shows the same name and code as the shared constant, and fall back
 * to what the event definition itself declares. Job types and root elements are exactly what the shared
 * definition files are collected from, so a value found there becomes a [SharedValue] pointing at its
 * constant — the constant name depends on the value alone, whichever process declared it.
 */
internal class FlowFacetsFactory(
    private val names: Map<String, FlowNaming.Names>,
    private val definitions: RootElements,
) {

    fun of(node: FlowNodeDefinition): NodeFacets {
        val event = node as? FlowNodeDefinition.Event
        val callActivity = (node as? FlowNodeDefinition.Activity.CallActivity)?.definition
        return NodeFacets(
            jobType = node.jobType(),
            variables = node.variables.toVariableFacets(),
            calledProcessId = callActivity?.getValue()?.ifBlank { null },
            inputs = callActivity?.inputMappings.toMappingFacets(),
            outputs = callActivity?.outputMappings.toMappingFacets(),
            timer = event?.firstDefinition<EventDefinitionInstance.Timer>()?.toFacet(),
            message = node.messageReference()?.resolveName()?.sharedIn(definitions.messages),
            signal = event?.firstDefinition<EventDefinitionInstance.Signal>()?.resolveName()?.sharedIn(definitions.signals),
            error = event?.firstDefinition<EventDefinitionInstance.Error>()?.resolve()?.let { it.sharedIn(definitions.errors, it.name to it.code) },
            escalation = event?.firstDefinition<EventDefinitionInstance.Escalation>()?.resolve()?.let { it.sharedIn(definitions.escalations, it.name to it.code) },
            attachedTo = event?.attachedToRef?.let { names[it] }?.let { FlowEdge(it.propertyName, it.objectName) },
            isInterrupting = event?.isInterrupting(),
        )
    }

    private fun FlowNodeDefinition.jobType(): SharedValue<String>? = when (this) {
        is FlowNodeDefinition.Activity.Task -> implementation
        is FlowNodeDefinition.Event -> implementation
        else -> null
    }?.let { ServiceTaskDefinition(id, it) }
        ?.takeIf { it.getRawName().isNotBlank() }
        ?.let { serviceTask -> serviceTask.getValue().sharedIn(listOf(serviceTask)) }

    private fun List<VariableDefinition>.toVariableFacets(): List<VariableFacet> = groupBy { it.getRawName() }
        .toSortedMap()
        .map { (rawName, group) ->
            val subtype = VariableNameSubtype.chooseFor(group.map { it.direction }.toSet())
            VariableFacet(constantName = group.first().getName(), rawName = rawName, subtype = subtype)
        }

    private fun List<CallActivityDefinition.Mapping>?.toMappingFacets(): List<MappingFacet> = orEmpty()
        .filter { !it.target.isNullOrBlank() }
        .sortedBy { it.target!!.toUpperSnakeCase() }
        .map { MappingFacet(constantName = it.target!!.toUpperSnakeCase(), target = it.target, source = it.source, sourceExpression = it.sourceExpression) }

    private inline fun <reified T : EventDefinitionInstance> FlowNodeDefinition.Event.firstDefinition(): T? = eventDefinitions.filterIsInstance<T>().firstOrNull()

    private fun EventDefinitionInstance.Timer.toFacet(): TimerFacet = TimerFacet(type = timerType?.label ?: "", expression = expression ?: "")

    private fun FlowNodeDefinition.messageReference(): MessageReference? = when (this) {
        is FlowNodeDefinition.Event -> firstDefinition<EventDefinitionInstance.Message>()?.reference
        is FlowNodeDefinition.Activity.Task -> message
        else -> null
    }

    private fun MessageReference.resolveName(): String? = messageName?.ifBlank { null }
        ?: definitions.messages.firstOrNull { it.id == messageRef }?.getRawName()?.ifBlank { null }

    private fun EventDefinitionInstance.Signal.resolveName(): String? = signalName?.ifBlank { null }
        ?: definitions.signals.firstOrNull { it.id == signalRef }?.getRawName()?.ifBlank { null }

    private fun EventDefinitionInstance.Error.resolve(): NamedCode? = definitions.errors.firstOrNull { it.id == errorRef }?.getValue()?.toNamedCode()
        ?: (errorName to (errorCode ?: "")).toNamedCode()

    private fun EventDefinitionInstance.Escalation.resolve(): NamedCode? = definitions.escalations.firstOrNull { it.id == escalationRef }?.getValue()?.toNamedCode()
        ?: (escalationName to (escalationCode ?: "")).toNamedCode()

    private fun Pair<String?, String>.toNamedCode(): NamedCode? = first?.ifBlank { null }?.let { NamedCode(name = it, code = second) }

    /**
     * The shared constant is the definition whose value equals [definitionValue] — the job type, the resolved
     * name, or name and code.
     */
    private fun <T> T.sharedIn(candidates: List<VariableMapping<*>>, definitionValue: Any? = this): SharedValue<T> {
        val definition = candidates.firstOrNull { it.getValue() == definitionValue }
        return SharedValue(this, definition?.let { SharedConstant(name = it.getName(), rawName = it.getRawName()) })
    }

    private fun FlowNodeDefinition.Event.isInterrupting(): Boolean? = when (shape) {
        EventShape.BOUNDARY_EVENT -> interrupting ?: true
        EventShape.START_EVENT -> interrupting
        else -> null
    }
}
