package io.miragon.bpmn.adapter.outbound.codegen.builder

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.JavaFile
import com.palantir.javapoet.TypeSpec
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
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

/**
 * Generates the type-safe API contract for a single BPMN process as a Java class file.
 * References shared BPMN types (BpmnTimer, BpmnError, etc.) from the `bpmn-to-code-runtime` artifact.
 */
internal class JavaProcessApiBuilder : CodeGenerationAdapter.AbstractProcessApiBuilder<TypeSpec.Builder>() {

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
        val className = modelApi.fileName()
        val rootClassBuilder = TypeSpec.classBuilder(className).addModifiers(PUBLIC, FINAL)

        objectWriters
            .filterKeys { ApiObjectSelection.includes(it, modelApi) }
            .forEach { (_, writer) -> writer.addTo(rootClassBuilder, modelApi) }

        val fileBuilder = JavaFile.builder(modelApi.packagePath, rootClassBuilder.build())
        val javaFile = fileBuilder.addFileComment(autoGenComment).build()

        val fileContent = buildString { javaFile.writeTo(this) }

        return GeneratedApiFile(
            fileName = "${modelApi.fileName()}.java",
            packagePath = modelApi.packagePath,
            content = fileContent,
            language = modelApi.outputLanguage,
            processId = modelApi.model.processId,
        )
    }

    private class ProcessIdWriter : ObjectWriter<TypeSpec.Builder> {

        override fun addTo(builder: TypeSpec.Builder, modelApi: BpmnModelApi) {
            val processIdClass = ClassName.get(RUNTIME_PACKAGE, "ProcessId")
            val fieldBuilder = FieldSpec.builder(processIdClass, "PROCESS_ID").addModifiers(PUBLIC, FINAL, STATIC)
            builder.addField(fieldBuilder.initializer("new \$T(\$S)", processIdClass, modelApi.model.processId).build())
        }
    }

    private class ProcessEngineWriter : ObjectWriter<TypeSpec.Builder> {

        override fun addTo(builder: TypeSpec.Builder, modelApi: BpmnModelApi) {
            val bpmnEngineClass = ClassName.get(RUNTIME_PACKAGE, "BpmnEngine")
            val fieldBuilder = FieldSpec.builder(bpmnEngineClass, "PROCESS_ENGINE")
                .addModifiers(PUBLIC, FINAL, STATIC)
                .initializer("\$T.\$L", bpmnEngineClass, modelApi.targetEngine.name)
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
            val variantsBuilder = TypeSpec.classBuilder("FlowVariants").addModifiers(PUBLIC, STATIC, FINAL)
                .addJavadoc("The {@code Flow} of each merged BPMN file, keyed by its {@code variantName}.\n")
            model.variants.forEach { variant ->
                variantsBuilder.addType(buildFlowClass(variant.graph, model.definitions, variant.variantName.toCamelCase()))
            }
            builder.addType(variantsBuilder.build())
        }
    }

    /**
     * Renders the process as a typed navigation graph: one nested class per element exposing its `id`,
     * `elementType` and display `name`, plus its reachable successors behind `then()`. Boundary events and
     * subprocess continuations are plain successors; every node is a direct child of `Flow`, and a subprocess
     * opens its interior via `start()`.
     */
    private fun buildFlowClass(graph: ProcessGraph, definitions: RootElements, className: String = "Flow"): TypeSpec {
        val flowBuilder = TypeSpec.classBuilder(className).addModifiers(PUBLIC, STATIC, FINAL)
            .addJavadoc(
                "Typed navigation over the process flow. Each element is a nested class exposing its {@code id}, " +
                    "{@code elementType} and display {@code name}, plus the elements reachable from it behind " +
                    "{@code then()} — so a full path is verified by the compiler and offered by autocomplete. " +
                    "Every element is a direct child of {@code Flow}, whatever its subprocess depth; " +
                    "a subprocess opens its interior via {@code start()}.\n",
            )
        JavaFlowWriter().write(flowBuilder, FlowGraphFactory.build(graph, definitions))
        return flowBuilder.build()
    }
}
