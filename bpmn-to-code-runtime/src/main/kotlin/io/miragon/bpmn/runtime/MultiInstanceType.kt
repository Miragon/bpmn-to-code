package io.miragon.bpmn.runtime

/**
 * Whether the instances of a multi-instance activity run one after another or at the same time.
 */
enum class MultiInstanceType {
    SEQUENTIAL,
    PARALLEL,
}
