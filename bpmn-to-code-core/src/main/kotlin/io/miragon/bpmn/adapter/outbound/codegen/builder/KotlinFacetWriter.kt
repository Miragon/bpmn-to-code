package io.miragon.bpmn.adapter.outbound.codegen.builder

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.MappingFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.NamedCode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.NodeFacets
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedConstant
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedValue
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.VariableFacet

/**
 * Emits a Kotlin `Flow` node's own data: plain properties (`JOB_TYPE`, `calledProcess`, `timer`, `message`, …)
 * and the nested holders (`Variables`, `Inputs`, `Outputs`). Job types, messages, signals, errors and
 * escalations refer to their shared definition constant. Cross-node references (`attachedTo`) are getters so
 * object initialisation never touches another node.
 */
internal class KotlinFacetWriter {

    fun properties(facets: NodeFacets): List<PropertySpec> = listOfNotNull(
        facets.jobType?.let { jobTypeProperty(it) },
        facets.calledProcessId?.let { wrappedProperty("calledProcess", "ProcessId", it) },
        facets.timer?.let { pairProperty("timer", "BpmnTimer", "type" to it.type, "timerValue" to it.expression) },
        facets.message?.let { sharedProperty("message", "MessageName", SharedDefinitionType.MESSAGES, it, ::wrappedInitializer) },
        facets.signal?.let { sharedProperty("signal", "SignalName", SharedDefinitionType.SIGNALS, it, ::wrappedInitializer) },
        facets.error?.let { sharedProperty("error", "BpmnError", SharedDefinitionType.ERRORS, it, ::namedCodeInitializer) },
        facets.escalation?.let { sharedProperty("escalation", "BpmnEscalation", SharedDefinitionType.ESCALATIONS, it, ::namedCodeInitializer) },
        facets.attachedTo?.let { attachedToProperty(it.objectName) },
        facets.isInterrupting?.let { PropertySpec.builder("isInterrupting", Boolean::class).initializer("%L", it).build() },
    )

    fun holders(facets: NodeFacets): List<TypeSpec> = listOfNotNull(
        facets.variables.takeIf { it.isNotEmpty() }?.let { variablesHolder(it) },
        facets.inputs.takeIf { it.isNotEmpty() }?.let { mappingsHolder("Inputs", it) },
        facets.outputs.takeIf { it.isNotEmpty() }?.let { mappingsHolder("Outputs", it) },
    )

    private fun jobTypeProperty(jobType: SharedValue<String>): PropertySpec = PropertySpec.builder("JOB_TYPE", String::class)
        .addModifiers(KModifier.CONST)
        .initializer(jobType.constant?.let { sharedReference(SharedDefinitionType.SERVICE_TASKS, it) } ?: kotlinStringLiteral(jobType.value))
        .build()

    /**
     * A property holding a shared definition refers to its constant, e.g. `val message: MessageName = Messages.X`;
     * only a value without a shared constant falls back to its [literal] form.
     */
    private fun <T> sharedProperty(
        name: String,
        wrapper: String,
        type: SharedDefinitionType,
        shared: SharedValue<T>,
        literal: (ClassName, T) -> CodeBlock,
    ): PropertySpec {
        val wrapperClass = ClassName(RUNTIME_PACKAGE, wrapper)
        val initializer = shared.constant?.let { sharedReference(type, it) } ?: literal(wrapperClass, shared.value)
        return PropertySpec.builder(name, wrapperClass).initializer(initializer).build()
    }

    /**
     * The shared types live in the same package as the Process API, so the plain name resolves without an import.
     */
    private fun sharedReference(type: SharedDefinitionType, constant: SharedConstant): CodeBlock = CodeBlock.of("%L.%N", type.typeName, constant.name)

    private fun wrappedProperty(name: String, wrapper: String, value: String): PropertySpec {
        val wrapperClass = ClassName(RUNTIME_PACKAGE, wrapper)
        return PropertySpec.builder(name, wrapperClass).initializer(wrappedInitializer(wrapperClass, value)).build()
    }

    private fun wrappedInitializer(wrapperClass: ClassName, value: String): CodeBlock = CodeBlock.of("%T(%L)", wrapperClass, kotlinStringLiteral(value))

    private fun pairProperty(name: String, wrapper: String, first: Pair<String, String>, second: Pair<String, String>): PropertySpec {
        val wrapperClass = ClassName(RUNTIME_PACKAGE, wrapper)
        val call = kotlinNamedCall(wrapperClass, first.first to kotlinStringLiteral(first.second), second.first to kotlinStringLiteral(second.second))
        return PropertySpec.builder(name, wrapperClass).initializer(kotlinInitializer(call)).build()
    }

    private fun namedCodeInitializer(wrapperClass: ClassName, value: NamedCode): CodeBlock = kotlinInitializer(kotlinNamedCall(wrapperClass, "name" to kotlinStringLiteral(value.name), "code" to kotlinStringLiteral(value.code)))

    private fun attachedToProperty(hostObjectName: String): PropertySpec = PropertySpec.builder("attachedTo", ClassName("", hostObjectName))
        .getter(FunSpec.getterBuilder().addStatement("return %N", hostObjectName).build())
        .build()

    private fun variablesHolder(variables: List<VariableFacet>): TypeSpec {
        val holder = TypeSpec.objectBuilder("Variables")
        variables.forEach { variable ->
            val subtypeClass = ClassName(RUNTIME_PACKAGE, "VariableName").nestedClass(variable.subtype.simpleName)
            holder.addProperty(
                PropertySpec.builder(variable.constantName, subtypeClass)
                    .initializer("%T(%L)", subtypeClass, kotlinStringLiteral(variable.rawName))
                    .build(),
            )
        }
        return holder.build()
    }

    private fun mappingsHolder(holderName: String, mappings: List<MappingFacet>): TypeSpec {
        val mappingClass = ClassName(RUNTIME_PACKAGE, "InputOutputMapping")
        val holder = TypeSpec.objectBuilder(holderName)
        mappings.forEach { mapping ->
            holder.addProperty(PropertySpec.builder(mapping.constantName, mappingClass).initializer(kotlinInitializer(mappingCall(mappingClass, mapping))).build())
        }
        return holder.build()
    }

    private fun mappingCall(mappingClass: ClassName, mapping: MappingFacet): CodeBlock = kotlinNamedCall(
        mappingClass,
        "target" to kotlinStringLiteral(mapping.target),
        "source" to mapping.source?.let { kotlinStringLiteral(it) },
        "sourceExpression" to mapping.sourceExpression?.let { kotlinStringLiteral(it) },
    )

    private companion object {
        private const val RUNTIME_PACKAGE = "io.miragon.bpmn.runtime"
    }
}
