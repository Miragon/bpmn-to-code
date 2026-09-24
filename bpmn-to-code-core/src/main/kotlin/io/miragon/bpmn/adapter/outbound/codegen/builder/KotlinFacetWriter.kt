package io.miragon.bpmn.adapter.outbound.codegen.builder

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.MappingFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.NamedCode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.NodeFacets
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.VariableFacet

/**
 * Emits a Kotlin `Flow` node's own data: plain properties (`JOB_TYPE`, `calledProcess`, `timer`, `message`, …)
 * and the nested holders (`Variables`, `Inputs`, `Outputs`). Cross-node references (`attachedTo`) are getters so
 * object initialisation never touches another node.
 */
internal class KotlinFacetWriter {

    fun properties(facets: NodeFacets): List<PropertySpec> = listOfNotNull(
        facets.jobType?.let { jobTypeProperty(it) },
        facets.calledProcessId?.let { wrappedProperty("calledProcess", "ProcessId", it) },
        facets.timer?.let { pairProperty("timer", "BpmnTimer", "type" to it.type, "timerValue" to it.expression) },
        facets.message?.let { wrappedProperty("message", "MessageName", it) },
        facets.signal?.let { wrappedProperty("signal", "SignalName", it) },
        facets.error?.let { namedCodeProperty("error", "BpmnError", it) },
        facets.escalation?.let { namedCodeProperty("escalation", "BpmnEscalation", it) },
        facets.attachedTo?.let { attachedToProperty(it.objectName) },
        facets.isInterrupting?.let { PropertySpec.builder("isInterrupting", Boolean::class).initializer("%L", it).build() },
    )

    fun holders(facets: NodeFacets): List<TypeSpec> = listOfNotNull(
        facets.variables.takeIf { it.isNotEmpty() }?.let { variablesHolder(it) },
        facets.inputs.takeIf { it.isNotEmpty() }?.let { mappingsHolder("Inputs", it) },
        facets.outputs.takeIf { it.isNotEmpty() }?.let { mappingsHolder("Outputs", it) },
    )

    private fun jobTypeProperty(jobType: String): PropertySpec = PropertySpec.builder("JOB_TYPE", String::class)
        .addModifiers(KModifier.CONST)
        .initializer("%L", kotlinStringLiteral(jobType))
        .build()

    private fun wrappedProperty(name: String, wrapper: String, value: String): PropertySpec {
        val wrapperClass = ClassName(RUNTIME_PACKAGE, wrapper)
        return PropertySpec.builder(name, wrapperClass).initializer("%T(%L)", wrapperClass, kotlinStringLiteral(value)).build()
    }

    private fun pairProperty(name: String, wrapper: String, first: Pair<String, String>, second: Pair<String, String>): PropertySpec {
        val wrapperClass = ClassName(RUNTIME_PACKAGE, wrapper)
        val call = kotlinNamedCall(wrapperClass, first.first to kotlinStringLiteral(first.second), second.first to kotlinStringLiteral(second.second))
        return PropertySpec.builder(name, wrapperClass).initializer(kotlinInitializer(call)).build()
    }

    private fun namedCodeProperty(name: String, wrapper: String, value: NamedCode): PropertySpec = pairProperty(name, wrapper, "name" to value.name, "code" to value.code)

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
