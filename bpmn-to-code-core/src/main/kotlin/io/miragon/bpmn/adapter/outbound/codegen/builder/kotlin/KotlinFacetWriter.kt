package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinCodeFormat.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.shared.KotlinErrorsWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.shared.KotlinEscalationsWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.shared.KotlinMessagesWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.shared.KotlinServiceTasksWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.shared.KotlinSignalsWriter
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.MappingFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.NodeFacets
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.TimerFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.VariableFacet

/**
 * Emits a Kotlin `FlowNodes` node's own data: the facet properties (`jobType`, `calledProcess`, `timer`, `message`, …)
 * implementing their runtime facet interfaces, and the nested holders (`Variables`, `Inputs`, `Outputs`). Job types, messages, signals, errors and
 * escalations refer to their shared definition constant. A boundary event's `attachedTo` and `isInterrupting`
 * implement `BoundaryEvent`; `attachedTo` is a getter so object initialisation never touches another node.
 */
internal class KotlinFacetWriter {

    fun properties(facets: NodeFacets): List<PropertySpec> = listOfNotNull(
        facets.jobType?.let { KotlinServiceTasksWriter.nodeProperty(name = "jobType", shared = it) },
        facets.calledProcessId?.let { calledProcessProperty(it) },
        facets.timer?.let { timerProperty(it) },
        facets.message?.let { KotlinMessagesWriter.nodeProperty(name = "message", shared = it) },
        facets.signal?.let { KotlinSignalsWriter.nodeProperty(name = "signal", shared = it) },
        facets.error?.let { KotlinErrorsWriter.nodeProperty(name = "error", shared = it) },
        facets.escalation?.let { KotlinEscalationsWriter.nodeProperty(name = "escalation", shared = it) },
        facets.attachedTo?.let { attachedToProperty(it.objectName) },
        facets.isInterrupting?.let { isInterruptingProperty(it, overridesBoundaryEvent = facets.attachedTo != null) },
    )

    fun holders(facets: NodeFacets): List<TypeSpec> = listOfNotNull(
        facets.variables.takeIf { it.isNotEmpty() }?.let { variablesHolder(it) },
        facets.inputs.takeIf { it.isNotEmpty() }?.let { mappingsHolder("Inputs", it) },
        facets.outputs.takeIf { it.isNotEmpty() }?.let { mappingsHolder("Outputs", it) },
    )

    fun superinterfaces(facets: NodeFacets): List<ClassName> = facets.facetInterfaces.map { ClassName(KotlinRuntimeTypes.PACKAGE, it.typeName) }

    private fun calledProcessProperty(calledProcessId: String): PropertySpec {
        val processIdClass = KotlinRuntimeTypes.PROCESS_ID
        val initializer = CodeBlock.of("%T(%L)", processIdClass, stringLiteral(calledProcessId))
        return PropertySpec.builder("calledProcess", processIdClass, KModifier.OVERRIDE).initializer(initializer).build()
    }

    private fun timerProperty(timer: TimerFacet): PropertySpec {
        val timerClass = KotlinRuntimeTypes.BPMN_TIMER
        val type = CodeBlock.of("%T.%L", KotlinRuntimeTypes.TIMER_TYPE, timer.type.name)
        val initializer = KotlinCodeFormat.namedCall(timerClass, "type" to type, "timerValue" to stringLiteral(timer.expression), placement = KotlinCodeFormat.Placement.INITIALIZER)
        return PropertySpec.builder("timer", timerClass, KModifier.OVERRIDE).initializer(initializer).build()
    }

    private fun attachedToProperty(hostObjectName: String): PropertySpec = PropertySpec.builder("attachedTo", ClassName("", hostObjectName))
        .addModifiers(KModifier.OVERRIDE)
        .getter(FunSpec.getterBuilder().addStatement("return %N", hostObjectName).build()).build()

    private fun isInterruptingProperty(isInterrupting: Boolean, overridesBoundaryEvent: Boolean): PropertySpec {
        val property = PropertySpec.builder("isInterrupting", Boolean::class).initializer("%L", isInterrupting)
        if (overridesBoundaryEvent) property.addModifiers(KModifier.OVERRIDE)
        return property.build()
    }

    private fun variablesHolder(variables: List<VariableFacet>): TypeSpec {
        val holder = TypeSpec.objectBuilder("Variables")
        variables.forEach { variable ->
            val subtypeClass = KotlinRuntimeTypes.VARIABLE_NAME.nestedClass(variable.subtype.simpleName)
            holder.addProperty(
                PropertySpec.builder(variable.constantName, subtypeClass)
                    .initializer("%T(%N.%N)", subtypeClass, KotlinNamesHolder.NAME, variable.constantName).build(),
            )
        }
        return holder.addType(KotlinNamesHolder(variables.map { it.constantName to it.rawName }).build()).build()
    }

    private fun mappingsHolder(holderName: String, mappings: List<MappingFacet>): TypeSpec {
        val mappingClass = KotlinRuntimeTypes.INPUT_OUTPUT_MAPPING
        val holder = TypeSpec.objectBuilder(holderName)
        mappings.forEach { mapping ->
            holder.addProperty(PropertySpec.builder(mapping.constantName, mappingClass).initializer(mappingInitializer(mappingClass, mapping)).build())
        }
        return holder.build()
    }

    private fun mappingInitializer(mappingClass: ClassName, mapping: MappingFacet): CodeBlock = KotlinCodeFormat.namedCall(
        mappingClass,
        "target" to stringLiteral(mapping.target),
        "source" to mapping.source?.let { stringLiteral(it) },
        "sourceExpression" to mapping.sourceExpression?.let { stringLiteral(it) },
        placement = KotlinCodeFormat.Placement.INITIALIZER,
    )
}
