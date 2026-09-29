package io.miragon.bpmn.runtime

/**
 * BPMN timer definition type: [DURATION] fires after elapsed time, [DATE] at an instant, [CYCLE] repeatedly.
 */
enum class TimerType {
    DATE,
    DURATION,
    CYCLE,
}
