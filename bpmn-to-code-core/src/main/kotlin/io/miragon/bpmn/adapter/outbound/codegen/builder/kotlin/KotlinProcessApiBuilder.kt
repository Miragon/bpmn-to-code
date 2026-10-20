package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin

import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.ProcessApiBuilder
import io.miragon.bpmn.adapter.outbound.codegen.ProcessApiGraph
import io.miragon.bpmn.adapter.outbound.codegen.ProcessId
import io.miragon.bpmn.adapter.outbound.codegen.SourceCode
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.poet.KotlinFlowWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.poet.KotlinSourceFile
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph
import io.miragon.bpmn.domain.shared.ProcessEngine

/**
 * Generates the type-safe API contract for a single BPMN process as a Kotlin object file.
 * References shared BPMN types (BpmnTimer, BpmnErrorDefinition, etc.) from the `bpmn-to-code-runtime` artifact.
 */
internal class KotlinProcessApiBuilder : ProcessApiBuilder {

    companion object {
        private const val PROCESS_ID = "PROCESS_ID"
    }

    override fun render(api: ProcessApiGraph): SourceCode {
        val rootObjectBuilder = TypeSpec.objectBuilder(api.typeName.value)
            .addProperty(processIdProperty(api.processId))
            .addProperty(processEngineProperty(api.processEngine))
        api.flow?.let { rootObjectBuilder.addType(buildFlowObject(it)) }
        return KotlinSourceFile.render(type = rootObjectBuilder.build(), packagePath = api.packagePath)
    }

    private fun processIdProperty(processId: ProcessId): PropertySpec {
        val processIdClass = KotlinRuntimeTypes.PROCESS_ID
        return PropertySpec.builder(PROCESS_ID, processIdClass)
            .initializer("%T(%S)", processIdClass, processId.value).build()
    }

    private fun processEngineProperty(processEngine: ProcessEngine): PropertySpec {
        val bpmnEngineClass = KotlinRuntimeTypes.BPMN_ENGINE
        return PropertySpec.builder("PROCESS_ENGINE", bpmnEngineClass)
            .initializer("%T.%L", bpmnEngineClass, processEngine.name).build()
    }

    /**
     * Renders the process as a typed navigation graph: one nested object per element exposing its `id`,
     * `elementType` and display `name`, plus its reachable successors behind `next`. Boundary events and
     * subprocess continuations are plain successors; every node is a direct child of `FlowNodes`, and a subprocess
     * opens its interior via `startEvents`.
     */
    private fun buildFlowObject(graph: FlowGraph): TypeSpec {
        val flowBuilder = TypeSpec.objectBuilder("FlowNodes").addKdoc("Typed navigation over the process flow: one nested object per BPMN element.")
        KotlinFlowWriter().write(flowBuilder, graph)
        return flowBuilder.build()
    }
}
