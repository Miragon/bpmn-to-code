package io.miragon.bpmn.domain.shared

/**
 * The flow nodes and sequence flows owned by one BPMN scope.
 *
 * BPMN calls this a `bpmn:FlowElementsContainer`: a process and a sub-process are containers in exactly
 * the same sense, each owning its children *and* the flows between them. Naming it once is what lets the
 * process model, its variants and [FlowNodeDefinition.Activity.SubProcess] share the concept instead of
 * each carrying the two lists apart — and what lets a reader hand back one value rather than a pair.
 *
 * This is the store. [ProcessGraph] is the flattened projection over it.
 */
data class FlowScope(
    val flowNodes: List<FlowNodeDefinition> = emptyList(),
    val sequenceFlows: List<SequenceFlowDefinition> = emptyList(),
)
