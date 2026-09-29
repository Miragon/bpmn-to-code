package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin

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
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.TimerFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.VariableFacet

/**
 * Emits a Kotlin `FlowNodes` node's own data: constants (`JOB_TYPE`, `CALLED_PROCESS`, `TIMER`, `MESSAGE`, …)
 * and the nested holders (`Variables`, `Inputs`, `Outputs`). Job types, messages, signals, errors and
 * escalations refer to their shared definition constant. A boundary event's `attachedTo` and `isInterrupting`
 * implement `BoundaryEvent`; `attachedTo` is a getter so object initialisation never touches another node.
 */
internal class KotlinFacetWriter {

    fun properties(facets: NodeFacets): List<PropertySpec> = listOfNotNull(
        facets.jobType?.let { jobTypeProperty(it) },
        facets.calledProcessId?.let { wrappedProperty(name = "CALLED_PROCESS", wrapper = "ProcessId", value = it) },
        facets.timer?.let { timerProperty(it) },
        facets.message?.let { sharedProperty(name = "MESSAGE", wrapper = "MessageName", type = SharedDefinitionType.MESSAGES, shared = it, literal = ::wrappedInitializer) },
        facets.signal?.let { sharedProperty(name = "SIGNAL", wrapper = "SignalName", type = SharedDefinitionType.SIGNALS, shared = it, literal = ::wrappedInitializer) },
        facets.error?.let { sharedProperty(name = "ERROR", wrapper = "BpmnErrorDefinition", type = SharedDefinitionType.ERRORS, shared = it, literal = ::namedCodeInitializer) },
        facets.escalation?.let { sharedProperty(name = "ESCALATION", wrapper = "BpmnEscalationDefinition", type = SharedDefinitionType.ESCALATIONS, shared = it, literal = ::namedCodeInitializer) },
        facets.attachedTo?.let { attachedToProperty(it.objectName) },
        facets.isInterrupting?.let { isInterruptingProperty(it, overridesBoundaryEvent = facets.attachedTo != null) },
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

    private fun timerProperty(timer: TimerFacet): PropertySpec {
        val timerClass = ClassName(RUNTIME_PACKAGE, "BpmnTimer")
        val type = CodeBlock.of("%T.%L", ClassName(RUNTIME_PACKAGE, "TimerType"), timer.type.name)
        val initializer = kotlinNamedInitializer(timerClass, "type" to type, "timerValue" to kotlinStringLiteral(timer.expression))
        return PropertySpec.builder("TIMER", timerClass).initializer(initializer).build()
    }

    private fun namedCodeInitializer(wrapperClass: ClassName, value: NamedCode): CodeBlock = kotlinNamedInitializer(wrapperClass, "name" to kotlinStringLiteral(value.name), "code" to kotlinStringLiteral(value.code))

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
            val subtypeClass = ClassName(RUNTIME_PACKAGE, "VariableName").nestedClass(variable.subtype.simpleName)
            holder.addProperty(
                PropertySpec.builder(variable.constantName, subtypeClass)
                    .initializer("%T(%N.%N)", subtypeClass, KotlinNamesHolder.NAME, variable.constantName).build(),
            )
        }
        return holder.addType(KotlinNamesHolder(variables.map { it.constantName to it.rawName }).build()).build()
    }

    private fun mappingsHolder(holderName: String, mappings: List<MappingFacet>): TypeSpec {
        val mappingClass = ClassName(RUNTIME_PACKAGE, "InputOutputMapping")
        val holder = TypeSpec.objectBuilder(holderName)
        mappings.forEach { mapping ->
            holder.addProperty(PropertySpec.builder(mapping.constantName, mappingClass).initializer(mappingInitializer(mappingClass, mapping)).build())
        }
        return holder.build()
    }

    private fun mappingInitializer(mappingClass: ClassName, mapping: MappingFacet): CodeBlock = kotlinNamedInitializer(
        mappingClass,
        "target" to kotlinStringLiteral(mapping.target),
        "source" to mapping.source?.let { kotlinStringLiteral(it) },
        "sourceExpression" to mapping.sourceExpression?.let { kotlinStringLiteral(it) },
    )

    private companion object {
        private const val RUNTIME_PACKAGE = "io.miragon.bpmn.runtime"
    }
}
