package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinSyntax.identifier
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinSyntax.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinTemplateModel.Argument
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinTemplateModel.Call
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinTemplateModel.Property
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedConstant
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedValue
import io.miragon.bpmn.adapter.outbound.codegen.template.TypeReferences

/**
 * How a shared constant is declared in its Kotlin object, and how a flow node refers to it. The shared objects live
 * in the Process API's package, so a reference needs no import.
 */
internal class KotlinSharedReferences(private val t: TypeReferences) {

    fun elementType(type: SharedDefinitionType): String = when (type) {
        SharedDefinitionType.SERVICE_TASKS, SharedDefinitionType.PROCESS_VARIABLES -> t.of("String")
        SharedDefinitionType.MESSAGES -> t.of("MessageName")
        SharedDefinitionType.SIGNALS -> t.of("SignalName")
        SharedDefinitionType.ERRORS -> t.of("BpmnErrorDefinition")
        SharedDefinitionType.ESCALATIONS -> t.of("BpmnEscalationDefinition")
    }

    /**
     * A property holding a value of [type] named [name]: job types and variable names are `const`, so annotation
     * arguments can use them.
     */
    fun property(type: SharedDefinitionType, modifiers: String, name: String, value: String, code: String?): Property {
        val elementType = elementType(type)
        return when (type) {
            SharedDefinitionType.SERVICE_TASKS, SharedDefinitionType.PROCESS_VARIABLES -> {
                Property(modifiers = modifiers, name = name, type = elementType, value = stringLiteral(value))
            }

            SharedDefinitionType.MESSAGES, SharedDefinitionType.SIGNALS -> {
                Property(modifiers = modifiers, name = name, type = elementType, value = "$elementType(${stringLiteral(value)})")
            }

            SharedDefinitionType.ERRORS, SharedDefinitionType.ESCALATIONS -> {
                val arguments = listOf(
                    Argument(name = "name", value = stringLiteral(value)),
                    Argument(name = "code", value = stringLiteral(code.orEmpty())),
                )
                Property(modifiers = modifiers, name = name, type = elementType, call = Call(function = elementType, arguments = arguments))
            }
        }
    }

    fun reference(type: SharedDefinitionType, constant: SharedConstant): String = "${type.typeName}.${identifier(constant.name)}"

    /**
     * A node's property [name] of [type]: its shared constant, or the value itself when no shared constant holds it.
     */
    fun nodeProperty(type: SharedDefinitionType, name: String, shared: SharedValue<String>): Property {
        val constant = shared.constant ?: return property(type = type, modifiers = OVERRIDE, name = name, value = shared.value, code = null)
        return Property(modifiers = OVERRIDE, name = name, type = elementType(type), value = reference(type, constant))
    }

    fun nodePropertyWithCode(type: SharedDefinitionType, name: String, shared: SharedValue<Pair<String, String>>): Property {
        val constant = shared.constant
        if (constant == null) {
            val (value, code) = shared.value
            return property(type = type, modifiers = OVERRIDE, name = name, value = value, code = code)
        }
        return Property(modifiers = OVERRIDE, name = name, type = elementType(type), value = reference(type, constant))
    }

    private companion object {
        const val OVERRIDE = "override "
    }
}
