package io.miragon.bpmn.adapter.outbound.codegen.builder

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
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.VariableFacet
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

/**
 * Emits a Java `Flow` node's own data: fields (`JOB_TYPE`, `calledProcess`, `timer`, `message`, …), the
 * `attachedTo()` factory for a boundary event's host, and the nested holders (`Variables`, `Inputs`, `Outputs`).
 * Job types, messages, signals, errors and escalations refer to their shared definition constant.
 */
internal class JavaFacetWriter {

    fun fields(facets: NodeFacets): List<FieldSpec> = listOfNotNull(
        facets.jobType?.let { jobTypeField(it) },
        facets.calledProcessId?.let { wrappedField("calledProcess", "ProcessId", it) },
        facets.timer?.let { pairField("timer", "BpmnTimer", it.type, it.expression) },
        facets.message?.let { sharedField("message", "MessageName", SharedDefinitionType.MESSAGES, it, ::wrappedInitializer) },
        facets.signal?.let { sharedField("signal", "SignalName", SharedDefinitionType.SIGNALS, it, ::wrappedInitializer) },
        facets.error?.let { sharedField("error", "BpmnError", SharedDefinitionType.ERRORS, it, ::namedCodeInitializer) },
        facets.escalation?.let { sharedField("escalation", "BpmnEscalation", SharedDefinitionType.ESCALATIONS, it, ::namedCodeInitializer) },
        facets.isInterrupting?.let { FieldSpec.builder(TypeName.BOOLEAN, "isInterrupting", PUBLIC, FINAL).initializer("\$L", it).build() },
    )

    fun methods(facets: NodeFacets): List<MethodSpec> = listOfNotNull(
        facets.attachedTo?.let { attachedToMethod(it.objectName) },
    )

    fun holders(facets: NodeFacets): List<TypeSpec> = listOfNotNull(
        facets.variables.takeIf { it.isNotEmpty() }?.let { variablesHolder(it) },
        facets.inputs.takeIf { it.isNotEmpty() }?.let { mappingsHolder("Inputs", it) },
        facets.outputs.takeIf { it.isNotEmpty() }?.let { mappingsHolder("Outputs", it) },
    )

    private fun jobTypeField(jobType: SharedValue<String>): FieldSpec = FieldSpec.builder(String::class.java, "JOB_TYPE", PUBLIC, STATIC, FINAL)
        .initializer(jobType.constant?.let { sharedReference(SharedDefinitionType.SERVICE_TASKS, it) } ?: CodeBlock.of("\$S", jobType.value))
        .build()

    /**
     * A field holding a shared definition refers to its constant, e.g. `MessageName message = Messages.X`; only a
     * value without a shared constant falls back to its [literal] form.
     */
    private fun <T> sharedField(
        name: String,
        wrapper: String,
        type: SharedDefinitionType,
        shared: SharedValue<T>,
        literal: (ClassName, T) -> CodeBlock,
    ): FieldSpec {
        val wrapperClass = ClassName.get(RUNTIME_PACKAGE, wrapper)
        val initializer = shared.constant?.let { sharedReference(type, it) } ?: literal(wrapperClass, shared.value)
        return FieldSpec.builder(wrapperClass, name, PUBLIC, FINAL).initializer(initializer).build()
    }

    private fun sharedReference(type: SharedDefinitionType, constant: SharedConstant): CodeBlock = CodeBlock.of("\$T.\$N", ClassName.get("", type.typeName), constant.name)

    private fun wrappedField(name: String, wrapper: String, value: String): FieldSpec {
        val wrapperClass = ClassName.get(RUNTIME_PACKAGE, wrapper)
        return FieldSpec.builder(wrapperClass, name, PUBLIC, FINAL).initializer(wrappedInitializer(wrapperClass, value)).build()
    }

    private fun wrappedInitializer(wrapperClass: ClassName, value: String): CodeBlock = CodeBlock.of("new \$T(\$S)", wrapperClass, value)

    private fun namedCodeInitializer(wrapperClass: ClassName, value: NamedCode): CodeBlock = CodeBlock.of("new \$T(\$S, \$S)", wrapperClass, value.name, value.code)

    private fun pairField(name: String, wrapper: String, first: String, second: String): FieldSpec {
        val wrapperClass = ClassName.get(RUNTIME_PACKAGE, wrapper)
        return FieldSpec.builder(wrapperClass, name, PUBLIC, FINAL).initializer("new \$T(\$S, \$S)", wrapperClass, first, second).build()
    }

    private fun attachedToMethod(hostObjectName: String): MethodSpec {
        val hostClass = ClassName.get("", hostObjectName)
        return MethodSpec.methodBuilder("attachedTo").addModifiers(PUBLIC).returns(hostClass)
            .addStatement("return new \$T()", hostClass)
            .build()
    }

    private fun variablesHolder(variables: List<VariableFacet>): TypeSpec {
        val holder = TypeSpec.classBuilder("Variables").addModifiers(PUBLIC, STATIC, FINAL)
        variables.forEach { variable ->
            val subtypeClass = ClassName.get(RUNTIME_PACKAGE, "VariableName").nestedClass(variable.subtype.simpleName)
            holder.addField(
                FieldSpec.builder(subtypeClass, variable.constantName, PUBLIC, STATIC, FINAL)
                    .initializer("new \$T(\$S)", subtypeClass, variable.rawName)
                    .build(),
            )
        }
        return holder.build()
    }

    private fun mappingsHolder(holderName: String, mappings: List<MappingFacet>): TypeSpec {
        val mappingClass = ClassName.get(RUNTIME_PACKAGE, "InputOutputMapping")
        val holder = TypeSpec.classBuilder(holderName).addModifiers(PUBLIC, STATIC, FINAL)
        mappings.forEach { mapping ->
            holder.addField(
                FieldSpec.builder(mappingClass, mapping.constantName, PUBLIC, STATIC, FINAL)
                    .initializer(mappingInitializer(mappingClass, mapping))
                    .build(),
            )
        }
        return holder.build()
    }

    private fun mappingInitializer(mappingClass: ClassName, mapping: MappingFacet): CodeBlock = CodeBlock.builder()
        .add("new \$T(\$S, \$S, \$S)", mappingClass, mapping.target, mapping.source, mapping.sourceExpression)
        .build()

    private companion object {
        private const val RUNTIME_PACKAGE = "io.miragon.bpmn.runtime"
    }
}
