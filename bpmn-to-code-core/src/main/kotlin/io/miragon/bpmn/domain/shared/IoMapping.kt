package io.miragon.bpmn.domain.shared

/**
 * A node's input/output parameter mapping, normalised across engines: `zeebe:ioMapping` and
 * `camunda:inputOutput` both map onto this shape. Values are preserved verbatim, so a FEEL expression
 * (`=order.id`), a JUEL expression (`${'$'}{order.id}`) and a static value are all round-tripped unchanged.
 */
data class IoMapping(val inputs: List<Parameter> = emptyList(), val outputs: List<Parameter> = emptyList()) {

    fun isEmpty(): Boolean = inputs.isEmpty() && outputs.isEmpty()

    /**
     * Every parameter writes a variable: an input one inside the node, an output one back into the process.
     */
    fun toVariables(): List<VariableDefinition> {
        val inputVariables = inputs.map { VariableDefinition(name = it.target, direction = VariableDirection.INPUT, valueExpression = it.source) }
        val outputVariables = outputs.map { VariableDefinition(name = it.target, direction = VariableDirection.OUTPUT, valueExpression = it.source) }
        return inputVariables + outputVariables
    }

    /**
     * One input or output parameter. [target] is the variable being written, [source] the expression or
     * static value it is bound to (absent for `camunda:outputParameter` bodies that carry no value).
     */
    data class Parameter(val target: String, val source: String? = null)
}
