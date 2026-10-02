package io.miragon.bpmn.runtime

/**
 * [VariableDefinitions] of a generated Kotlin node: every variable declared through [input], [output] or
 * [inOut] registers itself, so [all] lists them in declaration order without the generator repeating them.
 */
abstract class RegisteredVariableDefinitions : VariableDefinitions() {

    private val registered = mutableListOf<VariableName>()

    final override val all: List<VariableName>
        get() = registered.toList()

    protected fun input(name: String): VariableName.Input = register(VariableName.Input(name))

    protected fun output(name: String): VariableName.Output = register(VariableName.Output(name))

    protected fun inOut(name: String): VariableName.InOut = register(VariableName.InOut(name))

    private fun <VARIABLE : VariableName> register(variable: VARIABLE): VARIABLE {
        registered += variable
        return variable
    }
}
