package io.miragon.bpmn.adapter.outbound.json.model

import kotlinx.serialization.Serializable

/**
 * A node's input/output parameter mapping ([#74](https://github.com/Miragon/bpmn-to-code/issues/74)):
 * `zeebe:ioMapping` and `camunda:inputOutput` both normalise onto this shape.
 */
@Serializable
internal data class IoMappingJson(
    val inputs: List<Parameter> = emptyList(),
    val outputs: List<Parameter> = emptyList(),
) {

    /**
     * [target] is the variable being written, [source] the expression or static value bound to it.
     */
    @Serializable
    data class Parameter(
        val target: String,
        val source: String? = null,
    )
}
