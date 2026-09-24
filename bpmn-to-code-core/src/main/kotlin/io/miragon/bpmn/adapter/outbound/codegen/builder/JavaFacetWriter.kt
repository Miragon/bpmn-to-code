package io.miragon.bpmn.adapter.outbound.codegen.builder

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.TypeName
import com.palantir.javapoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.MappingFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.NamedCode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.NodeFacets
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.VariableFacet
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

/**
 * Emits a Java `Flow` node's own data: fields (`JOB_TYPE`, `calledProcess`, `timer`, `message`, …), the
 * `attachedTo()` factory for a boundary event's host, and the nested holders (`Variables`, `Inputs`, `Outputs`).
 */
internal class JavaFacetWriter {

    fun fields(facets: NodeFacets): List<FieldSpec> = listOfNotNull(
        facets.jobType?.let { jobTypeField(it) },
        facets.calledProcessId?.let { wrappedField("calledProcess", "ProcessId", it) },
        facets.timer?.let { pairField("timer", "BpmnTimer", it.type, it.expression) },
        facets.message?.let { wrappedField("message", "MessageName", it) },
        facets.signal?.let { wrappedField("signal", "SignalName", it) },
        facets.error?.let { namedCodeField("error", "BpmnError", it) },
        facets.escalation?.let { namedCodeField("escalation", "BpmnEscalation", it) },
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

    private fun jobTypeField(jobType: String): FieldSpec = FieldSpec.builder(String::class.java, "JOB_TYPE", PUBLIC, STATIC, FINAL)
        .initializer("\$S", jobType)
        .build()

    private fun wrappedField(name: String, wrapper: String, value: String): FieldSpec {
        val wrapperClass = ClassName.get(RUNTIME_PACKAGE, wrapper)
        return FieldSpec.builder(wrapperClass, name, PUBLIC, FINAL).initializer("new \$T(\$S)", wrapperClass, value).build()
    }

    private fun pairField(name: String, wrapper: String, first: String, second: String): FieldSpec {
        val wrapperClass = ClassName.get(RUNTIME_PACKAGE, wrapper)
        return FieldSpec.builder(wrapperClass, name, PUBLIC, FINAL).initializer("new \$T(\$S, \$S)", wrapperClass, first, second).build()
    }

    private fun namedCodeField(name: String, wrapper: String, value: NamedCode): FieldSpec = pairField(name, wrapper, value.name, value.code)

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
