package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.ProcessApiBuilder
import io.miragon.bpmn.adapter.outbound.codegen.ProcessApiGraph
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph
import io.miragon.bpmn.domain.shared.ProcessEngine
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

/**
 * Generates the type-safe API contract for a single BPMN process as a Java class file.
 * References shared BPMN types (BpmnTimer, BpmnErrorDefinition, etc.) from the `bpmn-to-code-runtime` artifact.
 */
internal class JavaProcessApiBuilder : ProcessApiBuilder {

    companion object {
        private const val PROCESS_ID = "PROCESS_ID"
    }

    override fun render(api: ProcessApiGraph): String {
        val rootClassBuilder = JavaConstantHolder(api.typeName).builder()
            .addField(processIdField(api.processId))
            .addField(processEngineField(api.processEngine))
        api.flow?.let { rootClassBuilder.addType(buildFlowClass(it)) }
        return JavaSourceFile.render(type = rootClassBuilder.build(), packagePath = api.packagePath)
    }

    private fun processIdField(processId: String): FieldSpec {
        val processIdClass = JavaRuntimeTypes.PROCESS_ID
        val fieldBuilder = FieldSpec.builder(processIdClass, PROCESS_ID).addModifiers(PUBLIC, FINAL, STATIC)
        return fieldBuilder.initializer($$"new $T($S)", processIdClass, processId).build()
    }

    private fun processEngineField(processEngine: ProcessEngine): FieldSpec {
        val bpmnEngineClass = JavaRuntimeTypes.BPMN_ENGINE
        val fieldBuilder = FieldSpec.builder(bpmnEngineClass, "PROCESS_ENGINE")
            .addModifiers(PUBLIC, FINAL, STATIC).initializer($$"$T.$L", bpmnEngineClass, processEngine.name)
        return fieldBuilder.build()
    }

    /**
     * Renders the process as a typed navigation graph: one nested class per element exposing its `id`,
     * `elementType` and display `name`, plus its reachable successors behind `getNext()`. Boundary events and
     * subprocess continuations are plain successors; every node is a direct child of `FlowNodes`, and a subprocess
     * opens its interior via `getStartEvents()`.
     */
    private fun buildFlowClass(graph: FlowGraph): TypeSpec {
        val flowBuilder = JavaConstantHolder("FlowNodes").builder(STATIC).addJavadoc("Typed navigation over the process flow: one nested class per BPMN element.\n")
        JavaFlowWriter().write(flowBuilder, graph)
        return flowBuilder.build()
    }
}
