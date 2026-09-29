package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionsBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.shared.CSharpErrorsWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.shared.CSharpEscalationsWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.shared.CSharpMessagesWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.shared.CSharpServiceTasksWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.shared.CSharpSignalsWriter
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.SharedDefinitionsApi

/**
 * Generates one C# static class per kind of engine-global identifier, shared by all Process APIs of a run.
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
    )

    override fun buildApiFiles(api: SharedDefinitionsApi): List<GeneratedApiFile> = writers
        .filter { it.shouldWrite(api.definitions) }
        .map { sharedDefinitionWriter ->
            CSharpSourceFile.render(typeName = sharedDefinitionWriter.type.typeName, packagePath = api.packagePath) { writer ->
                sharedDefinitionWriter.write(writer, api.definitions)
            }
        }
}
