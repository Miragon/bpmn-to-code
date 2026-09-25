package io.miragon.bpmn.adapter.outbound.codegen

/**
 * The types of the shared definition files, generated once per run next to the Process APIs. `Flow` nodes
 * reference their constants by these names, so the files and the references cannot drift apart.
 */
internal enum class SharedDefinitionType(val typeName: String) {
    SERVICE_TASKS("ServiceTasks"),
    MESSAGES("Messages"),
    SIGNALS("Signals"),
    ERRORS("Errors"),
    ESCALATIONS("Escalations"),
}
