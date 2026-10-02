package io.miragon.bpmn.runtime

/**
 * The variables a generated node declares, behind its `Variables` holder: [all] of them, those it reads
 * ([inputs]) and those it writes ([outputs]). A [VariableName.InOut] is read and written, so it is in both.
 */
abstract class VariableDefinitions {

    abstract val all: List<VariableName>

    val inputs: List<VariableName>
        get() = all.filter { it !is VariableName.Output }

    val outputs: List<VariableName>
        get() = all.filter { it !is VariableName.Input }
}
