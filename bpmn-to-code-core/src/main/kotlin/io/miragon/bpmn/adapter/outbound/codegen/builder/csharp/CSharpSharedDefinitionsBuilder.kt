package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionsBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.writer.CSharpSourceFile
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.writer.shared.CSharpErrorsWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.writer.shared.CSharpEscalationsWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.writer.shared.CSharpMessagesWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.writer.shared.CSharpProcessVariablesWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.writer.shared.CSharpServiceTasksWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.writer.shared.CSharpSignalsWriter
import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph

/**
 * Renders the C# static class of one kind of engine-global identifier, shared by all Process APIs of a run.
 * Plain `const string`s need none of the runtime types [CSharpProcessApiBuilder] inlines per file, so the
 * classes are the same for every process.
 */
internal class CSharpSharedDefinitionsBuilder : SharedDefinitionsBuilder {

    private val writers = listOf(
        CSharpServiceTasksWriter,
        CSharpMessagesWriter,
        CSharpSignalsWriter,
        CSharpErrorsWriter,
        CSharpEscalationsWriter,
        CSharpProcessVariablesWriter,
    ).associateBy { it.type }

    override fun render(group: SharedDefinitionsGraph.Group, packagePath: String): String {
        val sharedDefinitionWriter = writers.getValue(group.type)
        return CSharpSourceFile.render(packagePath = packagePath) { writer ->
            sharedDefinitionWriter.write(writer, group.entries)
        }
    }
}
