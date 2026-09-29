package io.miragon.bpmn.domain.shared

/**
 * The called-process binding of a `bpmn:callActivity`: the target process id plus the variable mappings
 * propagated in and out of it (`camunda:in` / `camunda:out` for Camunda 7 / Operaton, `zeebe:ioMapping`
 * for Zeebe).
 */
data class CallActivityDefinition(
    val id: String?,
    val calledElement: String?,
    val mappings: List<Mapping> = emptyList(),
    val propagateAllInputVariables: Boolean? = null,
    val propagateAllOutputVariables: Boolean? = null,
) {
    fun hasCalledElement() = calledElement != null

    val inputMappings get() = mappings.filter { it.direction == VariableDirection.INPUT }
    val outputMappings get() = mappings.filter { it.direction == VariableDirection.OUTPUT }

    /**
     * One variable passed into or out of the called process (`camunda:in` / `camunda:out`,
     * `zeebe:ioMapping`).
     */
    data class Mapping(
        val direction: VariableDirection,
        val source: String? = null,
        val sourceExpression: String? = null,
        val target: String? = null,
    )
}
