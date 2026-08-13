package io.miragon.bpmn.adapter.outbound.codegen

/**
 * The sections a generated Process API can contain.
 *
 * A catalogue of names, nothing else. Which of them a given run actually emits is decided by
 * [ApiObjectSelection] — that is a question about a model, and one day about what the caller asked for,
 * neither of which is a property of the name.
 */
internal enum class ApiObjectType {

    PROCESS_ID,
    PROCESS_ENGINE,
    ELEMENTS,
    FLOWS,
    RELATIONS,
    VARIANTS,
    CALL_ACTIVITIES,
    MESSAGES,
    SERVICE_TASKS,
    TIMERS,
    ERRORS,
    ESCALATIONS,
    COMPENSATIONS,
    SIGNALS,
    VARIABLES,
}
