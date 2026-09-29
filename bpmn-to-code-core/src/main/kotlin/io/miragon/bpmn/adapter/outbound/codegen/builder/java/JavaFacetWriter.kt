package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.TypeName
import com.palantir.javapoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.MappingFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.NamedCode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.NodeFacets
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedConstant
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedValue
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.TimerFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.VariableFacet
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

/**
 * Emits a Java `FlowNodes` node's own data: static constants (`JOB_TYPE`, `CALLED_PROCESS`, `TIMER`, `MESSAGE`, …), the
 * `getAttachedTo()`/`isInterrupting()` getters of a boundary event, and the nested holders (`Variables`, `Inputs`, `Outputs`).
 * Job types, messages, signals, errors and escalations refer to their shared definition constant.
 */
internal class JavaFacetWriter {

    fun fields(facets: NodeFacets): List<FieldSpec> = listOfNotNull(
        facets.jobType?.let { jobTypeField(it) },
        facets.calledProcessId?.let { wrappedField(name = "CALLED_PROCESS", wrapper = JavaRuntimeTypes.PROCESS_ID, value = it) },
        facets.timer?.let { timerField(it) },
        facets.message?.let { sharedField(name = "MESSAGE", wrapper = JavaRuntimeTypes.MESSAGE_NAME, type = SharedDefinitionType.MESSAGES, shared = it, literal = ::wrappedInitializer) },
        facets.signal?.let { sharedField(name = "SIGNAL", wrapper = JavaRuntimeTypes.SIGNAL_NAME, type = SharedDefinitionType.SIGNALS, shared = it, literal = ::wrappedInitializer) },
        facets.error?.let { sharedField(name = "ERROR", wrapper = JavaRuntimeTypes.BPMN_ERROR_DEFINITION, type = SharedDefinitionType.ERRORS, shared = it, literal = ::namedCodeInitializer) },
        facets.escalation?.let { sharedField(name = "ESCALATION", wrapper = JavaRuntimeTypes.BPMN_ESCALATION_DEFINITION, type = SharedDefinitionType.ESCALATIONS, shared = it, literal = ::namedCodeInitializer) },
    )

    fun methods(facets: NodeFacets): List<MethodSpec> = listOfNotNull(
        facets.attachedTo?.let { JavaFlowNodeType(it.objectName) }?.let { host ->
            getter(name = "getAttachedTo", returnType = host.className, returnValue = host.instance(), overridesBoundaryEvent = true)
        },
        facets.isInterrupting?.let { getter(name = "isInterrupting", returnType = TypeName.BOOLEAN, returnValue = CodeBlock.of($$"$L", it), overridesBoundaryEvent = facets.attachedTo != null) },
    )

    fun holders(facets: NodeFacets): List<TypeSpec> = listOfNotNull(
        facets.variables.takeIf { it.isNotEmpty() }?.let { variablesHolder(it) },
        facets.inputs.takeIf { it.isNotEmpty() }?.let { mappingsHolder("Inputs", it) },
        facets.outputs.takeIf { it.isNotEmpty() }?.let { mappingsHolder("Outputs", it) },
    )

    private fun jobTypeField(jobType: SharedValue<String>): FieldSpec = FieldSpec.builder(String::class.java, "JOB_TYPE", PUBLIC, STATIC, FINAL)
        .initializer(jobType.constant?.let { sharedReference(SharedDefinitionType.SERVICE_TASKS, it) } ?: CodeBlock.of($$"$S", jobType.value))
        .build()

    /**
     * A field holding a shared definition refers to its constant, e.g. `MessageName MESSAGE = Messages.X`; only a
     * value without a shared constant falls back to its [literal] form.
     */
    private fun <T> sharedField(
        name: String,
        wrapper: ClassName,
        type: SharedDefinitionType,
        shared: SharedValue<T>,
        literal: (ClassName, T) -> CodeBlock,
    ): FieldSpec {
        val initializer = shared.constant?.let { sharedReference(type, it) } ?: literal(wrapper, shared.value)
        return FieldSpec.builder(wrapper, name, PUBLIC, STATIC, FINAL).initializer(initializer).build()
    }

    private fun sharedReference(type: SharedDefinitionType, constant: SharedConstant): CodeBlock = CodeBlock.of($$"$T.$N", ClassName.get("", type.typeName), constant.name)

    private fun wrappedField(name: String, wrapper: ClassName, value: String): FieldSpec = FieldSpec.builder(wrapper, name, PUBLIC, STATIC, FINAL).initializer(wrappedInitializer(wrapper, value)).build()

    private fun wrappedInitializer(wrapperClass: ClassName, value: String): CodeBlock = CodeBlock.of($$"new $T($S)", wrapperClass, value)

    private fun namedCodeInitializer(wrapperClass: ClassName, value: NamedCode): CodeBlock = CodeBlock.of($$"new $T($S, $S)", wrapperClass, value.name, value.code)

    private fun timerField(timer: TimerFacet): FieldSpec {
        val timerClass = JavaRuntimeTypes.BPMN_TIMER
        val timerTypeClass = JavaRuntimeTypes.TIMER_TYPE
        return FieldSpec.builder(timerClass, "TIMER", PUBLIC, STATIC, FINAL)
            .initializer($$"new $T($T.$L, $S)", timerClass, timerTypeClass, timer.type.name, timer.expression).build()
    }

    private fun getter(name: String, returnType: TypeName, returnValue: CodeBlock, overridesBoundaryEvent: Boolean): MethodSpec {
        val method = MethodSpec.methodBuilder(name).addModifiers(PUBLIC).returns(returnType).addStatement($$"return $L", returnValue)
        if (overridesBoundaryEvent) method.addAnnotation(Override::class.java)
        return method.build()
    }

    private fun variablesHolder(variables: List<VariableFacet>): TypeSpec {
        val holder = JavaConstantHolder("Variables").builder(STATIC)
        variables.forEach { variable ->
            val subtypeClass = JavaRuntimeTypes.VARIABLE_NAME.nestedClass(variable.subtype.simpleName)
            holder.addField(
                FieldSpec.builder(subtypeClass, variable.constantName, PUBLIC, STATIC, FINAL)
                    .initializer($$"new $T($N.$N)", subtypeClass, JavaNamesHolder.NAME, variable.constantName).build(),
            )
        }
        return holder.addType(JavaNamesHolder(variables.map { it.constantName to it.rawName }).build()).build()
    }

    private fun mappingsHolder(holderName: String, mappings: List<MappingFacet>): TypeSpec {
        val mappingClass = JavaRuntimeTypes.INPUT_OUTPUT_MAPPING
        val holder = JavaConstantHolder(holderName).builder(STATIC)
        mappings.forEach { mapping ->
            holder.addField(
                FieldSpec.builder(mappingClass, mapping.constantName, PUBLIC, STATIC, FINAL)
                    .initializer(mappingInitializer(mappingClass, mapping)).build(),
            )
        }
        return holder.build()
    }

    private fun mappingInitializer(mappingClass: ClassName, mapping: MappingFacet): CodeBlock = CodeBlock.builder()
        .add($$"new $T($S, $S, $S)", mappingClass, mapping.target, mapping.source, mapping.sourceExpression).build()
}
