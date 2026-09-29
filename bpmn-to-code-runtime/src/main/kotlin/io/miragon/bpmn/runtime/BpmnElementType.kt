package io.miragon.bpmn.runtime

/**
 * BPMN element type of a generated `FlowNodes` node. For events, [Event.eventType] carries the event definition.
 */
enum class BpmnElementType {
    SERVICE_TASK,
    USER_TASK,
    RECEIVE_TASK,
    SEND_TASK,
    SCRIPT_TASK,
    MANUAL_TASK,
    BUSINESS_RULE_TASK,
    TASK,
    EXCLUSIVE_GATEWAY,
    PARALLEL_GATEWAY,
    INCLUSIVE_GATEWAY,
    EVENT_BASED_GATEWAY,
    COMPLEX_GATEWAY,
    SUB_PROCESS,
    EVENT_SUB_PROCESS,
    TRANSACTION,
    CALL_ACTIVITY,
    START_EVENT,
    END_EVENT,
    INTERMEDIATE_CATCH_EVENT,
    INTERMEDIATE_THROW_EVENT,
    BOUNDARY_EVENT,
    UNKNOWN,
}
