package io.miragon.bpmn.runtime

/**
 * A [FlowNode] that declares process variables, exposed behind [variables].
 *
 * A generated node narrows [variables] to its own `Variables` holder, so `node.variables.MY_VARIABLE` stays
 * typed while [HasVariables] adds a generic entry point on top.
 */
interface HasVariables : FlowNode {
    val variables: VariableDefinitions

    companion object {

        /**
         * The names of the variables [nodes] declare, each once — whatever direction a node uses it in.
         */
        @JvmStatic
        fun distinctVariablesOf(nodes: List<FlowNode>): List<String> {
            val nodesWithVariables = nodes.filterIsInstance<HasVariables>()
            val variables = nodesWithVariables.flatMap { it.variables.all }
            return variables.map { it.value }.distinct()
        }
    }
}
