package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.ProcessApiBuilder
import io.miragon.bpmn.adapter.outbound.codegen.ProcessApiGraph
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph
import io.miragon.bpmn.domain.shared.ProcessEngine

/**
 * Generates the process API for a single BPMN process as a C# file.
 *
 * The shape matches the Kotlin and Java builders: constant registries plus the typed `FlowNodes` navigation.
 * There is no C# runtime package; the handful of runtime types every node references
 * ([CSharpRuntimeTypes]) is emitted into the file itself as a nested `Runtime` class, so the generated file
 * still has no dependencies at all.
 */
internal class CSharpProcessApiBuilder : ProcessApiBuilder {

    override fun render(api: ProcessApiGraph): String {
        val flow = api.flow
        val sections = listOfNotNull<(CSharpWriter) -> Unit>(
            { writer -> writer.constant("ProcessId", api.processId) },
            { writer -> writeProcessEngine(writer, api.processEngine) },
            { writer -> writeRuntimeTypes(writer) },
            flow?.let { graph -> { writer -> writeFlow(writer, graph) } },
        )
        return CSharpSourceFile.render(packagePath = api.packagePath) { writer ->
            writer.staticClass(api.typeName) {
                writer.forEachSeparated(sections) { section -> section(writer) }
            }
        }
    }

    private fun writeProcessEngine(writer: CSharpWriter, processEngine: ProcessEngine) {
        val engineType = CSharpRuntimeTypes.BPMN_ENGINE
        writer.constantExpression(name = "ProcessEngine", expression = CSharpRuntimeTypes.enumMember(engineType, processEngine.name), type = engineType)
    }

    private fun writeRuntimeTypes(writer: CSharpWriter) {
        writer.docComment("The runtime types this API's flow nodes are built from; inlined so the file needs no package.")
        writer.staticClass(CSharpRuntimeTypes.CLASS_NAME) {
            CSharpRuntimeTypes.SOURCE.lines().forEach { writer.line(it) }
        }
    }

    /**
     * Renders the process as a typed navigation graph: one nested singleton class per element exposing its
     * `Id`, `ElementType` and `Name`, its facets and its successors behind `Next`; every node is a direct child of
     * `FlowNodes`, and a subprocess opens its interior via `Start`.
     */
    private fun writeFlow(writer: CSharpWriter, graph: FlowGraph) {
        writer.docComment("Typed navigation over the process flow: one nested singleton class per BPMN element, reached as FlowNodes.Element.Instance.")
        writer.staticClass("FlowNodes") {
            CSharpFlowWriter(writer).write(graph)
        }
    }
}
