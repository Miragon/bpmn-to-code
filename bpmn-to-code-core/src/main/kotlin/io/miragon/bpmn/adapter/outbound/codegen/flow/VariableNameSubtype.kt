package io.miragon.bpmn.adapter.outbound.codegen.flow

import io.miragon.bpmn.domain.shared.VariableDirection

/**
 * Nested subtype of the runtime `VariableName` sealed interface chosen per variable: `simpleName` is the nested
 * class name the generated code references.
 */
enum class VariableNameSubtype(val simpleName: String) {
    INPUT("Input"),
    OUTPUT("Output"),
    IN_OUT("InOut"),
    ;

    companion object {
        fun chooseFor(directions: Set<VariableDirection>): VariableNameSubtype {
            val hasInput = VariableDirection.INPUT in directions
            val hasOutput = VariableDirection.OUTPUT in directions
            return when {
                hasInput && hasOutput -> IN_OUT
                hasInput -> INPUT
                hasOutput -> OUTPUT
                else -> error("Unexpected variable directions: $directions")
            }
        }
    }
}
