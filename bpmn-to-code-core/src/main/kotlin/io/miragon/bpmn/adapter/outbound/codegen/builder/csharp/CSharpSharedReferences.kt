package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpCodeFormat.disambiguated
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpCodeFormat.pascalCase
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpCodeFormat.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedConstant
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedValue

/**
 * How a shared constant is declared in its C# class, and how a flow node refers to it. Job types and variable names
 * are `const string`, so attribute arguments and switch labels can use them; the other kinds are the runtime types the
 * nodes carry, as in the Kotlin and Java output.
 */
internal object CSharpSharedReferences {

    fun elementType(type: SharedDefinitionType): String = when (type) {
        SharedDefinitionType.SERVICE_TASKS, SharedDefinitionType.PROCESS_VARIABLES -> "string"
        SharedDefinitionType.MESSAGES -> CSharpRuntimeTypes.MESSAGE_NAME
        SharedDefinitionType.SIGNALS -> CSharpRuntimeTypes.SIGNAL_NAME
        SharedDefinitionType.ERRORS -> CSharpRuntimeTypes.BPMN_ERROR_DEFINITION
        SharedDefinitionType.ESCALATIONS -> CSharpRuntimeTypes.BPMN_ESCALATION_DEFINITION
    }

    fun modifiers(type: SharedDefinitionType): String = when (type) {
        SharedDefinitionType.SERVICE_TASKS, SharedDefinitionType.PROCESS_VARIABLES -> "const"
        else -> "static readonly"
    }

    /**
     * A value of [type]: a string literal for the `const` kinds, else the construction of its runtime type.
     */
    fun value(type: SharedDefinitionType, value: String, code: String?): String = when (type) {
        SharedDefinitionType.SERVICE_TASKS, SharedDefinitionType.PROCESS_VARIABLES -> stringLiteral(value)
        SharedDefinitionType.MESSAGES, SharedDefinitionType.SIGNALS -> "new(${stringLiteral(value)})"
        SharedDefinitionType.ERRORS, SharedDefinitionType.ESCALATIONS -> "new(${stringLiteral(value)}, ${stringLiteral(code.orEmpty())})"
    }

    fun memberName(type: SharedDefinitionType, constantName: String): String = disambiguated(pascalCase(constantName), type.typeName)

    fun reference(type: SharedDefinitionType, constant: SharedConstant): String = "${type.typeName}.${memberName(type, constant.name)}"

    /**
     * A node's value of [type]: its shared constant, or the value itself when no shared constant holds it.
     */
    fun nodeValue(type: SharedDefinitionType, shared: SharedValue<String>): String {
        val constant = shared.constant ?: return value(type = type, value = shared.value, code = null)
        return reference(type, constant)
    }

    fun nodeValueWithCode(type: SharedDefinitionType, shared: SharedValue<Pair<String, String>>): String {
        val constant = shared.constant
        if (constant == null) {
            val (name, code) = shared.value
            return value(type = type, value = name, code = code)
        }
        return reference(type, constant)
    }
}
