package io.miragon.bpmn.runtime

/**
 * Marks a boundary event. It shows up among its host's successors (`Next`), but no sequence flow leads to it —
 * this marker tells the two apart.
 */
interface BoundaryEvent : FlowNode
