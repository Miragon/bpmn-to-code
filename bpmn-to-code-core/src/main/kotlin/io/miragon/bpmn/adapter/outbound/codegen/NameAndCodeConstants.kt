package io.miragon.bpmn.adapter.outbound.codegen

import io.miragon.bpmn.domain.shared.VariableMapping

/**
 * The raw `Names` constants behind a name-and-code definition such as `BpmnErrorDefinition` — one for the name, one for the code.
 */
internal class NameAndCodeConstants(private val definition: VariableMapping<Pair<String, String>>) {

    val nameConstant = "${definition.getName()}_NAME"
    val codeConstant = "${definition.getName()}_CODE"

    fun rawValues(): List<Pair<String, String>> {
        val (name, code) = definition.getValue()
        return listOf(nameConstant to name, codeConstant to code)
    }
}
