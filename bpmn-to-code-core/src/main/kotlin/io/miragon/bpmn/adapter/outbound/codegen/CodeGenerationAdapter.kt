package io.miragon.bpmn.adapter.outbound.codegen

import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpProcessApiBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpSharedDefinitionsBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaProcessApiBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaSharedDefinitionsBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinProcessApiBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinSharedDefinitionsBuilder
import io.miragon.bpmn.application.port.outbound.GenerateApiCodePort
import io.miragon.bpmn.domain.BpmnModelApi
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.SharedDefinitionsApi
import io.miragon.bpmn.domain.shared.OutputLanguage

internal class CodeGenerationAdapter(
    private val processApiBuilders: Map<OutputLanguage, ProcessApiBuilder> = Companion.processApiBuilders,
    private val sharedDefinitionsBuilders: Map<OutputLanguage, SharedDefinitionsBuilder> = Companion.sharedDefinitionsBuilders,
) : GenerateApiCodePort {

    override fun generateCode(modelApi: BpmnModelApi): List<GeneratedApiFile> {
        val language = modelApi.outputLanguage
        val processApiBuilder = processApiBuilders[language] ?: throw IllegalArgumentException("$language is not supported")
        val processFile = processApiBuilder.buildApiFile(modelApi)
        return listOf(processFile)
    }

    override fun generateSharedCode(api: SharedDefinitionsApi): List<GeneratedApiFile> {
        val language = api.outputLanguage
        val sharedDefinitionsBuilder = sharedDefinitionsBuilders[language] ?: throw IllegalArgumentException("$language is not supported")
        return sharedDefinitionsBuilder.buildApiFiles(api)
    }

    companion object {
        val processApiBuilders: Map<OutputLanguage, ProcessApiBuilder> = mapOf(
            OutputLanguage.KOTLIN to KotlinProcessApiBuilder(),
            OutputLanguage.JAVA to JavaProcessApiBuilder(),
            OutputLanguage.CSHARP to CSharpProcessApiBuilder(),
        )
        val sharedDefinitionsBuilders: Map<OutputLanguage, SharedDefinitionsBuilder> = mapOf(
            OutputLanguage.KOTLIN to KotlinSharedDefinitionsBuilder(),
            OutputLanguage.JAVA to JavaSharedDefinitionsBuilder(),
            OutputLanguage.CSHARP to CSharpSharedDefinitionsBuilder(),
        )
    }
}
