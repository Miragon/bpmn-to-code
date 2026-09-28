package io.miragon.bpmn.adapter.outbound.codegen.builder

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.ApiObjectSelection
import io.miragon.bpmn.adapter.outbound.codegen.ApiObjectType
import io.miragon.bpmn.adapter.outbound.codegen.CodeGenerationAdapter
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraphFactory
import io.miragon.bpmn.adapter.outbound.codegen.writer.ObjectWriter
import io.miragon.bpmn.domain.BpmnModelApi
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.shared.ProcessGraph
import io.miragon.bpmn.domain.shared.RootElements
import io.miragon.bpmn.domain.utils.StringUtils.toCamelCase

/**
 * Generates the type-safe API contract for a single BPMN process as a Kotlin object file.
 * References shared BPMN types (BpmnTimer, BpmnError, etc.) from the `bpmn-to-code-runtime` artifact.
 */
internal class KotlinProcessApiBuilder : CodeGenerationAdapter.AbstractProcessApiBuilder<TypeSpec.Builder>() {

    companion object {
        private const val RUNTIME_PACKAGE = "io.miragon.bpmn.runtime"
    }

    private val objectWriters: Map<ApiObjectType, ObjectWriter<TypeSpec.Builder>> = mapOf(
        ApiObjectType.PROCESS_ID to ProcessIdWriter(),
        ApiObjectType.PROCESS_ENGINE to ProcessEngineWriter(),
        ApiObjectType.FLOW to FlowWriter(),
        ApiObjectType.FLOW_VARIANTS to FlowVariantsWriter(),
    )

    override fun buildApiFile(modelApi: BpmnModelApi): GeneratedApiFile {
        val objectName = modelApi.fileName()
        val unusedAnnotation = AnnotationSpec.builder(Suppress::class).addMember("%S", "unused").build()
        val rootObjectBuilder = TypeSpec.objectBuilder(objectName)
        val fileSpecBuilder = FileSpec.builder(modelApi.packagePath, objectName).addFileComment(autoGenComment)

        objectWriters
            .filterKeys { ApiObjectSelection.includes(it, modelApi) }
            .forEach { (_, writer) -> writer.addTo(rootObjectBuilder, modelApi) }

        fileSpecBuilder.addType(rootObjectBuilder.build()).addAnnotation(unusedAnnotation)
        val fileSpec = fileSpecBuilder.build()

        val content = buildString { fileSpec.writeTo(this) }.replace("public ", "")

        return GeneratedApiFile(
            fileName = "$objectName.kt",
            packagePath = modelApi.packagePath,
            content = content,
            language = modelApi.outputLanguage,
            processId = modelApi.model.processId,
        )
    }

    private inner class ProcessIdWriter : ObjectWriter<TypeSpec.Builder> {

        override fun addTo(builder: TypeSpec.Builder, modelApi: BpmnModelApi) {
            val processIdClass = ClassName(RUNTIME_PACKAGE, "ProcessId")
            val idProperty = PropertySpec.builder("PROCESS_ID", processIdClass)
                .initializer("%T(%L)", processIdClass, stringLiteral(modelApi.model.processId))
                .build()
            builder.addProperty(idProperty)
        }
    }

    private class ProcessEngineWriter : ObjectWriter<TypeSpec.Builder> {

        override fun addTo(builder: TypeSpec.Builder, modelApi: BpmnModelApi) {
            val bpmnEngineClass = ClassName(RUNTIME_PACKAGE, "BpmnEngine")
            val engineProperty = PropertySpec.builder("PROCESS_ENGINE", bpmnEngineClass)
                .initializer("%T.%L", bpmnEngineClass, modelApi.targetEngine.name)
                .build()
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
                .addKdoc("The `Flow` of each merged BPMN file, keyed by its `variantName`.")
            model.variants.forEach { variant ->
                variantsBuilder.addType(buildFlowObject(variant.graph, model.definitions, variant.variantName.toCamelCase()))
            }
            builder.addType(variantsBuilder.build())
        }
    }

    /**
     * Renders the process as a typed navigation graph: one nested object per element exposing its `id`,
     * `elementType` and display `name`, plus its reachable successors behind `then()`. Boundary events and
     * subprocess continuations are plain successors; every node is a direct child of `Flow`, and a subprocess
     * opens its interior via `start()`.
     */
    private fun buildFlowObject(graph: ProcessGraph, definitions: RootElements, objectName: String = "Flow"): TypeSpec {
        val flowBuilder = TypeSpec.objectBuilder(objectName)
            .addKdoc(
                "Typed navigation over the process flow.\n" +
                    "Each element is a nested object exposing its `id`, `elementType` and display `name`, plus the " +
                    "elements reachable from it behind `then()` — so a full path is verified by the compiler and " +
                    "offered by autocomplete. Every element is a direct child of `Flow`, whatever its subprocess " +
                    "depth; a subprocess opens its interior via `start()`.\n" +
                    "Intended for tooling, tests, and reasoning about the process shape.",
            )
        KotlinFlowWriter().write(flowBuilder, FlowGraphFactory.build(graph, definitions))
        return flowBuilder.build()
    }

    private fun stringLiteral(value: String): CodeBlock = kotlinStringLiteral(value)
}
