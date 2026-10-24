package io.miragon.bpmn.domain.shared

/**
 * Process engines supported by bpmn-to-code for api-generation
 */
enum class ProcessEngine(val displayName: String) {
    ZEEBE("Zeebe (Camunda 8)"),
    CAMUNDA_7("Camunda 7"),
    OPERATON("Operaton"),
}
