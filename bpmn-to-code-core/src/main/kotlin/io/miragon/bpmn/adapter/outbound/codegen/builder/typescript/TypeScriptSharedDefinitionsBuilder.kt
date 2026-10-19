package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionsBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.shared.TypeScriptSharedDefinitionWriters
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.SharedDefinitionsApi

internal class TypeScriptSharedDefinitionsBuilder : SharedDefinitionsBuilder {

    override fun buildApiFiles(api: SharedDefinitionsApi): List<GeneratedApiFile> {
        val writers = TypeScriptSharedDefinitionWriters.all.filter { it.shouldWrite(api.definitions) }
        return writers.map { sharedDefinitionWriter ->
            TypeScriptSourceFile.render(typeName = sharedDefinitionWriter.type.typeName, packagePath = api.packagePath) { writer ->
                sharedDefinitionWriter.write(writer, api.definitions)
            }
        }
    }
}
