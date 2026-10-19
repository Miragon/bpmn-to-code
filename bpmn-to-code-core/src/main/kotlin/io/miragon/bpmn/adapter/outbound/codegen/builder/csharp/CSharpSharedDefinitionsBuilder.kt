package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionsBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.shared.CSharpErrorsWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.shared.CSharpEscalationsWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.shared.CSharpMessagesWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.shared.CSharpProcessVariablesWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.shared.CSharpServiceTasksWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.shared.CSharpSignalsWriter
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.SharedDefinitionsApi

/**
 * Generates one C# static class per kind of engine-global identifier, shared by all Process APIs of a run.
 */
internal class CSharpSharedDefinitionsBuilder : SharedDefinitionsBuilder {

    private val writers = listOf(
        CSharpServiceTasksWriter,
        CSharpMessagesWriter,
        CSharpSignalsWriter,
        CSharpErrorsWriter,
        CSharpEscalationsWriter,
        CSharpProcessVariablesWriter,
    )

    override fun buildApiFiles(api: SharedDefinitionsApi): List<GeneratedApiFile> {
        val definitionFiles = writers.filter { it.shouldWrite(api.definitions) }.map { sharedDefinitionWriter ->
            CSharpSourceFile.render(typeName = sharedDefinitionWriter.type.typeName, packagePath = api.packagePath) { writer ->
                sharedDefinitionWriter.write(writer, api.definitions)
            }
        }
        return definitionFiles
    }
}
