package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.TypeSpec
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

    private val objectWriters: Map<ApiObjectType, ObjectWriter<TypeSpec.Builder>> = mapOf(
        ApiObjectType.PROCESS_ID to ProcessIdWriter(),
        ApiObjectType.PROCESS_ENGINE to ProcessEngineWriter(),
        ApiObjectType.FLOW to FlowWriter(),
        ApiObjectType.FLOW_VARIANTS to FlowVariantsWriter(),
    )

    override fun buildApiFile(modelApi: BpmnModelApi): GeneratedApiFile {
        val rootClassBuilder = JavaConstantHolder(modelApi.fileName()).builder()
        objectWriters
            .filterKeys { ApiObjectSelection.includes(it, modelApi) }
            .forEach { (_, writer) -> writer.addTo(rootClassBuilder, modelApi) }
        return JavaSourceFile.render(
            type = rootClassBuilder.build(),
            packagePath = modelApi.packagePath,
            processId = modelApi.model.processId,
        )
    }

    private class ProcessIdWriter : ObjectWriter<TypeSpec.Builder> {

        override fun addTo(builder: TypeSpec.Builder, modelApi: BpmnModelApi) {
            val processIdClass = JavaRuntimeTypes.PROCESS_ID
            val fieldBuilder = FieldSpec.builder(processIdClass, PROCESS_ID).addModifiers(PUBLIC, FINAL, STATIC)
            builder.addField(fieldBuilder.initializer($$"new $T($N.$N)", processIdClass, JavaNamesHolder.NAME, PROCESS_ID).build())
            builder.addType(JavaNamesHolder(listOf(PROCESS_ID to modelApi.model.processId)).build())
        }
    }

    private class ProcessEngineWriter : ObjectWriter<TypeSpec.Builder> {

        override fun addTo(builder: TypeSpec.Builder, modelApi: BpmnModelApi) {
            val bpmnEngineClass = JavaRuntimeTypes.BPMN_ENGINE
            val fieldBuilder = FieldSpec.builder(bpmnEngineClass, "PROCESS_ENGINE")
                .addModifiers(PUBLIC, FINAL, STATIC).initializer($$"$T.$L", bpmnEngineClass, modelApi.targetEngine.name)
            builder.addField(fieldBuilder.build())
        }
    }

    private inner class FlowWriter : ObjectWriter<TypeSpec.Builder> {

        override fun addTo(builder: TypeSpec.Builder, modelApi: BpmnModelApi) {
            val flowClass = buildFlowClass(modelApi.model.graph, modelApi.model.definitions)
            builder.addType(flowClass)
        }
    }

    private inner class FlowVariantsWriter : ObjectWriter<TypeSpec.Builder> {

        override fun addTo(builder: TypeSpec.Builder, modelApi: BpmnModelApi) {
            val model = modelApi.model
            val variantsBuilder = JavaConstantHolder("FlowVariants").builder(STATIC)
                .addJavadoc("The {@code FlowNodes} of each merged BPMN file, keyed by its {@code variantName}.\n")
            model.variants.forEach { variant ->
                variantsBuilder.addType(buildFlowClass(graph = variant.graph, definitions = model.definitions, className = variant.variantName.toCamelCase()))
            }
            builder.addType(variantsBuilder.build())
        }
    }

    /**
     * Renders the process as a typed navigation graph: one nested class per element exposing its `id`,
     * `elementType` and display `name`, plus its reachable successors behind `getNext()`. Boundary events and
     * subprocess continuations are plain successors; every node is a direct child of `FlowNodes`, and a subprocess
     * opens its interior via `getStartEvents()`.
     */
    private fun buildFlowClass(graph: ProcessGraph, definitions: RootElements, className: String = "FlowNodes"): TypeSpec {
        val flowBuilder = JavaConstantHolder(className).builder(STATIC).addJavadoc("Typed navigation over the process flow: one nested class per BPMN element.\n")
        JavaFlowWriter().write(flowBuilder, FlowGraphFactory.build(graph, definitions))
        return flowBuilder.build()
    }
}
