package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaSyntax.identifier
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaSyntax.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedConstant
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedValue
import io.miragon.bpmn.adapter.outbound.codegen.template.TypeReferences

/**
 * How a shared constant is declared in its Java class, and how a flow node refers to it.
 */
internal class JavaSharedReferences(private val t: TypeReferences) {

    fun elementType(type: SharedDefinitionType): String = when (type) {
        SharedDefinitionType.SERVICE_TASKS, SharedDefinitionType.PROCESS_VARIABLES -> t.of("String")
        SharedDefinitionType.MESSAGES -> t.of("MessageName")
        SharedDefinitionType.SIGNALS -> t.of("SignalName")
        SharedDefinitionType.ERRORS -> t.of("BpmnErrorDefinition")
        SharedDefinitionType.ESCALATIONS -> t.of("BpmnEscalationDefinition")
    }

    /**
     * The value of a constant of [type]: the name as it is, wrapped in its runtime type, or as name and code.
     */
    fun value(type: SharedDefinitionType, name: String, code: String?): String = when (type) {
        SharedDefinitionType.SERVICE_TASKS, SharedDefinitionType.PROCESS_VARIABLES -> stringLiteral(name)

        SharedDefinitionType.MESSAGES, SharedDefinitionType.SIGNALS -> "new ${elementType(type)}(${stringLiteral(name)})"

        SharedDefinitionType.ERRORS, SharedDefinitionType.ESCALATIONS -> {
            "new ${elementType(type)}(${stringLiteral(name)}, ${stringLiteral(code.orEmpty())})"
        }
    }

    fun reference(type: SharedDefinitionType, constant: SharedConstant): String = "${type.typeName}.${identifier(constant.name)}"

    /**
     * A node's value of [type]: its shared constant, or the value itself when no shared constant holds it.
     */
    fun nodeValue(type: SharedDefinitionType, shared: SharedValue<String>): String {
        val constant = shared.constant ?: return value(type, name = shared.value, code = null)
        return reference(type, constant)
    }

    fun nodeValueWithCode(type: SharedDefinitionType, shared: SharedValue<Pair<String, String>>): String {
        val constant = shared.constant ?: return value(type, name = shared.value.first, code = shared.value.second)
        return reference(type, constant)
    }
}
