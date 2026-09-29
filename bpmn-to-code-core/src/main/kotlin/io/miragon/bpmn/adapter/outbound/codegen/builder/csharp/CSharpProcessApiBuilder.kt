package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.ApiObjectSelection
import io.miragon.bpmn.adapter.outbound.codegen.ApiObjectType
import io.miragon.bpmn.adapter.outbound.codegen.ObjectWriter
import io.miragon.bpmn.adapter.outbound.codegen.ProcessApiBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpRuntimeTypes
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpWriter
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraphFactory
import io.miragon.bpmn.domain.BpmnModelApi
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.shared.ProcessGraph
import io.miragon.bpmn.domain.shared.RootElements
import io.miragon.bpmn.domain.utils.StringUtils.toCamelCase

/**
 * Generates the process API for a single BPMN process as a C# file.
 *
 * The shape matches the Kotlin and Java builders: constant registries plus the typed `FlowNodes` / `FlowVariants`
 * navigation. There is no C# runtime package; the handful of runtime types every node references
 * ([CSharpRuntimeTypes]) is emitted into the file itself as a nested `Runtime` class, so the generated file
 * still has no dependencies at all.
 */
internal class CSharpProcessApiBuilder : ProcessApiBuilder {

    private val objectWriters: Map<ApiObjectType, ObjectWriter<CSharpWriter>> = mapOf(
        ApiObjectType.PROCESS_ID to ProcessIdWriter(),
        ApiObjectType.PROCESS_ENGINE to ProcessEngineWriter(),
        ApiObjectType.FLOW to FlowWriter(),
        ApiObjectType.FLOW_VARIANTS to FlowVariantsWriter(),
    )

    override fun buildApiFile(modelApi: BpmnModelApi): GeneratedApiFile {
        val sections = objectWriters.filterKeys { ApiObjectSelection.includes(it, modelApi) }.values.toList()
        val (constants, nested) = sections.partition { it is ProcessIdWriter || it is ProcessEngineWriter }
        return CSharpSourceFile.render(
            typeName = modelApi.fileName(),
            packagePath = modelApi.packagePath,
            processId = modelApi.model.processId,
        ) { writer ->
            writer.staticClass(modelApi.fileName()) {
                writer.forEachSeparated(constants + RuntimeTypesWriter() + nested) { section -> section.addTo(writer, modelApi) }
            }
        }
    }

    private class ProcessIdWriter : ObjectWriter<CSharpWriter> {

        override fun addTo(builder: CSharpWriter, modelApi: BpmnModelApi) {
            builder.constant("ProcessId", modelApi.model.processId)
        }
    }

    private class ProcessEngineWriter : ObjectWriter<CSharpWriter> {

        override fun addTo(builder: CSharpWriter, modelApi: BpmnModelApi) {
            val engineType = CSharpRuntimeTypes.BPMN_ENGINE
            builder.constantExpression(name = "ProcessEngine", expression = CSharpRuntimeTypes.enumMember(engineType, modelApi.targetEngine.name), type = engineType)
        }
    }

    private class RuntimeTypesWriter : ObjectWriter<CSharpWriter> {

        override fun addTo(builder: CSharpWriter, modelApi: BpmnModelApi) {
            builder.docComment("The runtime types this API's flow nodes are built from; inlined so the file needs no package.")
            builder.staticClass(CSharpRuntimeTypes.CLASS_NAME) {
                CSharpRuntimeTypes.SOURCE.lines().forEach { builder.line(it) }
            }
        }
    }

    private inner class FlowWriter : ObjectWriter<CSharpWriter> {

        override fun addTo(builder: CSharpWriter, modelApi: BpmnModelApi) {
            writeFlow(builder = builder, graph = modelApi.model.graph, definitions = modelApi.model.definitions)
        }
    }

    private inner class FlowVariantsWriter : ObjectWriter<CSharpWriter> {

        override fun addTo(builder: CSharpWriter, modelApi: BpmnModelApi) {
            val model = modelApi.model
            builder.docComment("The FlowNodes of each merged BPMN file, keyed by its variantName.")
            builder.staticClass("FlowVariants") {
                builder.forEachSeparated(model.variants) { variant ->
                    writeFlow(builder = builder, graph = variant.graph, definitions = model.definitions, className = variant.variantName.toCamelCase())
                }
            }
        }
    }

    /**
     * Renders the process as a typed navigation graph: one nested singleton class per element exposing its
     * `Id`, `ElementType` and `Name`, its facets, its successors behind `Next`, its sequence flows behind
     * `OutgoingFlows`; every node is a direct child of `FlowNodes`, and a subprocess opens its interior via `Start`.
     */
    private fun writeFlow(builder: CSharpWriter, graph: ProcessGraph, definitions: RootElements, className: String = "FlowNodes") {
        builder.docComment("Typed navigation over the process flow: one nested singleton class per BPMN element, reached as FlowNodes.Element.Instance.")
        builder.staticClass(className) {
            CSharpFlowWriter(builder).write(FlowGraphFactory.build(graph, definitions))
        }
    }
}
