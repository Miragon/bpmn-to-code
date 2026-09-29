package io.miragon.bpmn.runtime

/**
 * Marks an event node and exposes its [eventType], so generic tooling can tell a timer from a terminate event
 * without knowing the node's facets.
 */
interface Event : FlowNode {
    val eventType: BpmnEventType
}
