package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeName
import com.palantir.javapoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.shared.JavaErrorsWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.shared.JavaEscalationsWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.shared.JavaMessagesWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.shared.JavaProcessVariablesWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.shared.JavaServiceTasksWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.shared.JavaSignalsWriter
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.MappingFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.NodeFacets
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedConstant
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedValue
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.TimerFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.VariableFacet
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PRIVATE
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

/**
 * Emits a Java `FlowNodes` node's own data: the facet getters (`getJobType()`, `getCalledProcess()`, `getTimer()`, `getVariables()`, …)
 * implementing their runtime facet interfaces, the `getAttachedTo()`/`isInterrupting()` getters of a boundary event, and the nested holders (`Variables`, `Inputs`, `Outputs`).
 * Job types, messages, signals, errors, escalations and variable names refer to their shared definition constant.
 */
internal class JavaFacetWriter {

    fun superinterfaces(facets: NodeFacets): List<ClassName> = facets.facetInterfaces.map { ClassName.get(JavaRuntimeTypes.PACKAGE, it.typeName) }

    fun methods(facets: NodeFacets): List<MethodSpec> = listOfNotNull(
        facets.jobType?.let { getter(name = "getJobType", returnType = STRING, returnValue = JavaServiceTasksWriter.nodeValue(it), overrides = true) },
        facets.calledProcessId?.let { calledProcessGetter(it) },
        facets.timer?.let { timerGetter(it) },
        facets.message?.let { getter(name = "getMessage", returnType = JavaRuntimeTypes.MESSAGE_NAME, returnValue = JavaMessagesWriter.nodeValue(it), overrides = true) },
        facets.signal?.let { getter(name = "getSignal", returnType = JavaRuntimeTypes.SIGNAL_NAME, returnValue = JavaSignalsWriter.nodeValue(it), overrides = true) },
        facets.error?.let { getter(name = "getError", returnType = JavaRuntimeTypes.BPMN_ERROR_DEFINITION, returnValue = JavaErrorsWriter.nodeValue(it), overrides = true) },
        facets.escalation?.let { escalationGetter(it) },
        facets.attachedTo?.let { JavaFlowNodeType(it.objectName) }?.let { host ->
            getter(name = "getAttachedTo", returnType = host.className, returnValue = host.instance(), overrides = true)
        },
        facets.isInterrupting?.let { getter(name = "isInterrupting", returnType = TypeName.BOOLEAN, returnValue = CodeBlock.of($$"$L", it), overrides = facets.attachedTo != null) },
        facets.variables.takeIf { it.isNotEmpty() }?.let {
            getter(name = "getVariables", returnType = VARIABLES_HOLDER, returnValue = CodeBlock.of($$"$N", VARIABLES_FIELD), overrides = true)
        },
    )

    fun fields(facets: NodeFacets): List<FieldSpec> = listOfNotNull(
        facets.variables.takeIf { it.isNotEmpty() }?.let {
            FieldSpec.builder(VARIABLES_HOLDER, VARIABLES_FIELD, PRIVATE, STATIC, FINAL).initializer($$"new $T()", VARIABLES_HOLDER).build()
        },
    )

    fun holders(facets: NodeFacets): List<TypeSpec> = listOfNotNull(
        facets.variables.takeIf { it.isNotEmpty() }?.let { variablesHolder(it) },
        facets.inputs.takeIf { it.isNotEmpty() }?.let { mappingsHolder("Inputs", it) },
        facets.outputs.takeIf { it.isNotEmpty() }?.let { mappingsHolder("Outputs", it) },
    )

    private fun calledProcessGetter(calledProcessId: String): MethodSpec {
        val processIdClass = JavaRuntimeTypes.PROCESS_ID
        val processId = CodeBlock.of($$"new $T($S)", processIdClass, calledProcessId)
        return getter(name = "getCalledProcess", returnType = processIdClass, returnValue = processId, overrides = true)
    }

    private fun timerGetter(timer: TimerFacet): MethodSpec {
        val timerClass = JavaRuntimeTypes.BPMN_TIMER
        val bpmnTimer = CodeBlock.of($$"new $T($T.$L, $S)", timerClass, JavaRuntimeTypes.TIMER_TYPE, timer.type.name, timer.expression)
        return getter(name = "getTimer", returnType = timerClass, returnValue = bpmnTimer, overrides = true)
    }

    private fun escalationGetter(escalation: SharedValue<Pair<String, String>>): MethodSpec {
        val escalationDefinition = JavaEscalationsWriter.nodeValue(escalation)
        return getter(name = "getEscalation", returnType = JavaRuntimeTypes.BPMN_ESCALATION_DEFINITION, returnValue = escalationDefinition, overrides = true)
    }

    private fun getter(name: String, returnType: TypeName, returnValue: CodeBlock, overrides: Boolean): MethodSpec {
        val method = MethodSpec.methodBuilder(name).addModifiers(PUBLIC).returns(returnType).addStatement($$"return $L", returnValue)
        if (overrides) method.addAnnotation(Override::class.java)
        return method.build()
    }

    private companion object {
        private val STRING: ClassName = ClassName.get(String::class.java)
        private val VARIABLES_HOLDER: ClassName = ClassName.get("", "Variables")
        private const val VARIABLES_FIELD = "VARIABLES"
    }

    private fun variablesHolder(variables: List<VariableFacet>): TypeSpec {
        val holder = JavaConstantHolder(VARIABLES_HOLDER.simpleName()).builder(STATIC).superclass(JavaRuntimeTypes.VARIABLE_DEFINITIONS)
        variables.forEach { variable ->
            val subtypeClass = JavaRuntimeTypes.VARIABLE_NAME.nestedClass(variable.subtype.simpleName)
            holder.addField(
                FieldSpec.builder(subtypeClass, variable.constantName, PUBLIC, STATIC, FINAL)
                    .initializer($$"new $T($L)", subtypeClass, JavaProcessVariablesWriter.reference(SharedConstant(variable.constantName))).build(),
            )
        }
        holder.addMethod(allVariables(variables))
        return holder.build()
    }

    private fun allVariables(variables: List<VariableFacet>): MethodSpec {
        val listClass = ClassName.get(List::class.java)
        val constants = variables.map { CodeBlock.of($$"$N", it.constantName) }
        return MethodSpec.methodBuilder("getAll").addAnnotation(Override::class.java).addModifiers(PUBLIC)
            .returns(ParameterizedTypeName.get(listClass, JavaRuntimeTypes.VARIABLE_NAME))
            .addStatement($$"return $T.of($L)", listClass, CodeBlock.join(constants, ", ")).build()
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
