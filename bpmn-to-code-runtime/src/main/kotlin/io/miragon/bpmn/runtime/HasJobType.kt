package io.miragon.bpmn.runtime

/**
 * A [FlowNode] implemented by a job worker (a service task or an event with an implementation) of [jobType].
 */
interface HasJobType : FlowNode {
    val jobType: String
}
