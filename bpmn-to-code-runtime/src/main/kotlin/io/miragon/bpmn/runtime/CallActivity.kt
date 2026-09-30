package io.miragon.bpmn.runtime

/**
 * A [FlowNode] that calls another process — a call activity — identified by [calledProcess].
 */
interface CallActivity : FlowNode {
    val calledProcess: ProcessId
}
