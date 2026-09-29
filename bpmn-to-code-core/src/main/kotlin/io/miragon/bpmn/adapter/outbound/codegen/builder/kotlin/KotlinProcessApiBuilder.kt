package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin

import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.ApiObjectSelection
import io.miragon.bpmn.adapter.outbound.codegen.ApiObjectType
import io.miragon.bpmn.adapter.outbound.codegen.ObjectWriter
import io.miragon.bpmn.adapter.outbound.codegen.ProcessApiBuilder
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraphFactory
import io.miragon.bpmn.domain.BpmnModelApi
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.shared.ProcessGraph
import io.miragon.bpmn.domain.shared.RootElements
import io.miragon.bpmn.domain.utils.StringUtils.toCamelCase

/**
 * Generates the type-safe API contract for a single BPMN process as a Kotlin object file.
 * References shared BPMN types (BpmnTimer, BpmnErrorDefinition, etc.) from the `bpmn-to-code-runtime` artifact.
 */
internal class KotlinProcessApiBuilder : ProcessApiBuilder {

    companion object {
        private const val PROCESS_ID = "PROCESS_ID"
    }

    private val objectWriters: Map<ApiObjectType, ObjectWriter<TypeSpec.Builder>> = mapOf(
        ApiObjectType.PROCESS_ID to ProcessIdWriter(),
        ApiObjectType.PROCESS_ENGINE to ProcessEngineWriter(),
        ApiObjectType.FLOW to FlowWriter(),
        ApiObjectType.FLOW_VARIANTS to FlowVariantsWriter(),
    )

    override fun buildApiFile(modelApi: BpmnModelApi): GeneratedApiFile {
        val rootObjectBuilder = TypeSpec.objectBuilder(modelApi.fileName())
        objectWriters
            .filterKeys { ApiObjectSelection.includes(it, modelApi) }
            .forEach { (_, writer) -> writer.addTo(rootObjectBuilder, modelApi) }
        return KotlinSourceFile.render(
            type = rootObjectBuilder.build(),
            packagePath = modelApi.packagePath,
            processId = modelApi.model.processId,
        )
    }

    private inner class ProcessIdWriter : ObjectWriter<TypeSpec.Builder> {

        override fun addTo(builder: TypeSpec.Builder, modelApi: BpmnModelApi) {
            val processIdClass = KotlinRuntimeTypes.PROCESS_ID
            val idProperty = PropertySpec.builder(PROCESS_ID, processIdClass)
                .initializer("%T(%N.%N)", processIdClass, KotlinNamesHolder.NAME, PROCESS_ID).build()
            builder.addProperty(idProperty)
            builder.addType(KotlinNamesHolder(listOf(PROCESS_ID to modelApi.model.processId)).build())
        }
    }

    private class ProcessEngineWriter : ObjectWriter<TypeSpec.Builder> {

        override fun addTo(builder: TypeSpec.Builder, modelApi: BpmnModelApi) {
            val bpmnEngineClass = KotlinRuntimeTypes.BPMN_ENGINE
            val engineProperty = PropertySpec.builder("PROCESS_ENGINE", bpmnEngineClass)
                .initializer("%T.%L", bpmnEngineClass, modelApi.targetEngine.name).build()
            builder.addProperty(engineProperty)
        }
    }

    private inner class FlowWriter : ObjectWriter<TypeSpec.Builder> {

        override fun addTo(builder: TypeSpec.Builder, modelApi: BpmnModelApi) {
            val flowObject = buildFlowObject(modelApi.model.graph, modelApi.model.definitions)
            builder.addType(flowObject)
        }
    }

    private inner class FlowVariantsWriter : ObjectWriter<TypeSpec.Builder> {

        override fun addTo(builder: TypeSpec.Builder, modelApi: BpmnModelApi) {
            val model = modelApi.model
            val variantsBuilder = TypeSpec.objectBuilder("FlowVariants")
                .addKdoc("The `FlowNodes` of each merged BPMN file, keyed by its `variantName`.")
            model.variants.forEach { variant ->
                variantsBuilder.addType(buildFlowObject(graph = variant.graph, definitions = model.definitions, objectName = variant.variantName.toCamelCase()))
            }
            builder.addType(variantsBuilder.build())
        }
    }

    /**
     * Renders the process as a typed navigation graph: one nested object per element exposing its `id`,
     * `elementType` and display `name`, plus its reachable successors behind `next`. Boundary events and
     * subprocess continuations are plain successors; every node is a direct child of `FlowNodes`, and a subprocess
     * opens its interior via `startEvents`.
     */
    private fun buildFlowObject(graph: ProcessGraph, definitions: RootElements, objectName: String = "FlowNodes"): TypeSpec {
        val flowBuilder = TypeSpec.objectBuilder(objectName).addKdoc("Typed navigation over the process flow: one nested object per BPMN element.")
        KotlinFlowWriter().write(flowBuilder, FlowGraphFactory.build(graph, definitions))
        return flowBuilder.build()
    }
}
