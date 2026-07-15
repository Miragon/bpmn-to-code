package io.miragon.bpmn.runtime

/**
 * A variable mapping between two scopes — e.g. the parent process and the called child process of a
 * call activity.
 *
 * [target] is the destination variable name (the name the variable gets in the receiving scope) and
 * is always present. The origin is either a plain [source] variable or a [sourceExpression]; which one
 * is populated depends on the engine:
 * - Camunda 7 / Operaton set [source] (plain variable) or [sourceExpression] (a `${...}` expression).
 * - Zeebe sets [source] to a FEEL expression (e.g. `=orderId`) and always leaves [sourceExpression] null.
 *
 * `toString()` returns [target], so `.target` is optional in string contexts.
 */
data class InputOutputMapping(
    val target: String,
    val source: String? = null,
    val sourceExpression: String? = null,
) {
    override fun toString(): String = target
}
