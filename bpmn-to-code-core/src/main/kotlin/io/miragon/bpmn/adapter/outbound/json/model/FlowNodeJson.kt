package io.miragon.bpmn.adapter.outbound.json.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * One BPMN flow node.
 *
 * [type] is the BPMN element's own name (`serviceTask`, `startEvent`, `subProcess`, …), so the object maps
 * onto `bpmn-moddle` by prefixing it with `bpmn:`. [incoming] and [outgoing] hold **sequence-flow ids**,
 * matching `bpmn:FlowNode.incoming` / `.outgoing`.
 *
 * The optional members are independent facets, each present only where BPMN allows it: containment
 * ([flowNodes] / [sequenceFlows]) on sub-processes, [eventDefinitions] on events, [multiInstance] and
 * [ioMapping] on activities, and so on. [extensions] and [engineAttributes] carry everything the engine
 * adds outside the BPMN namespace, verbatim.
 */
@Serializable
internal data class FlowNodeJson(
    val id: String,
    val type: String,
    val name: String? = null,
    val incoming: List<String> = emptyList(),
    val outgoing: List<String> = emptyList(),
    val default: String? = null,
    val attachedToRef: String? = null,
    val cancelActivity: Boolean? = null,
    val isInterrupting: Boolean? = null,
    val triggeredByEvent: Boolean? = null,
    val isForCompensation: Boolean? = null,
    val boundaryEventRefs: List<String> = emptyList(),
    val eventDefinitions: List<EventDefinitionJson> = emptyList(),
    val messageRef: String? = null,
    val implementation: ImplementationJson? = null,
    val calledElement: CalledElementJson? = null,
    val multiInstance: MultiInstanceJson? = null,
    val ioMapping: IoMappingJson? = null,
    val variables: List<VariableJson> = emptyList(),
    val flowNodes: List<FlowNodeJson> = emptyList(),
    val sequenceFlows: List<SequenceFlowJson> = emptyList(),
    val extensions: List<ExtensionJson> = emptyList(),
    val engineAttributes: Map<String, JsonElement> = emptyMap(),
)
