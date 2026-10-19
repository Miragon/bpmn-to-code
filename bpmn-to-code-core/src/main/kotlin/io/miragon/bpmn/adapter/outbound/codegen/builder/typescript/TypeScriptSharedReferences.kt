package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptSyntax.identifier
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptSyntax.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedConstant
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedValue

/**
 * How a shared constant is written in its TypeScript object, and how a flow node refers to it. Every shared object a
 * process API refers to is recorded, so the file imports exactly those.
 */
internal class TypeScriptSharedReferences {

    private val referencedTypes = sortedSetOf<String>()

    val imports: List<String>
        get() = referencedTypes.toList()

    fun value(type: SharedDefinitionType, name: String, code: String?): String {
        if (!type.carriesCode) return stringLiteral(name)
        return "{ name: ${stringLiteral(name)}, code: ${stringLiteral(code.orEmpty())} }"
    }

    fun reference(type: SharedDefinitionType, constant: SharedConstant): String {
        referencedTypes += type.typeName
        return "${type.typeName}.${identifier(constant.name)}"
    }

    fun nodeValue(type: SharedDefinitionType, shared: SharedValue<String>): String {
        val constant = shared.constant ?: return value(type, name = shared.value, code = null)
        return reference(type, constant)
    }

    fun nodeValueWithCode(type: SharedDefinitionType, shared: SharedValue<Pair<String, String>>): String {
        val constant = shared.constant ?: return value(type, name = shared.value.first, code = shared.value.second)
        return reference(type, constant)
    }
}
