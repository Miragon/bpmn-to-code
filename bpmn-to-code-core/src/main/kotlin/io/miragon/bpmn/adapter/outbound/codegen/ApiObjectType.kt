package io.miragon.bpmn.adapter.outbound.codegen

/**
 * The sections a generated Process API can contain.
 *
 * A catalogue of names, nothing else. Which of them a given run actually emits is decided by
 * [ApiObjectSelection] — that is a question about a model, and one day about what the caller asked for,
 * neither of which is a property of the name.
 *
 * Per-element data (ids, variables, timers, call-activity mappings) has no section of its own: it lives on
 * the nodes of [FLOW] (or of each variant's flow under [FLOW_VARIANTS]). Things shared across processes — root
 * elements and job types — are no section either: they are generated once per run as shared definitions.
 */
internal enum class ApiObjectType {

    PROCESS_ID,
    PROCESS_ENGINE,
    FLOW,
    FLOW_VARIANTS,
}
