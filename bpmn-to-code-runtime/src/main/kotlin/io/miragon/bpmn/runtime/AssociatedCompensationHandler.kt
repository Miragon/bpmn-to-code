package io.miragon.bpmn.runtime

/**
 * The compensation handler associated with the current compensation boundary event (BPMN `association`): it runs
 * when the event triggers, but no sequence flow leads there.
 */
data class AssociatedCompensationHandler<out TARGET : FlowNode>(override val target: TARGET) : Successor<TARGET>
