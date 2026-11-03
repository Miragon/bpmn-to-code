package io.miragon.bpmn.runtime

/**
 * A [FlowNode] that runs as multi-instance activity, so tooling can find every one without knowing the node types.
 */
interface MultiInstanceActivity : FlowNode {
    val multiInstance: BpmnMultiInstance
}
