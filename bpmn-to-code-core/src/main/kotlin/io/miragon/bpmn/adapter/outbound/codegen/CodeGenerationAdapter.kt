package io.miragon.bpmn.adapter.outbound.codegen

import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpProcessApiBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpSharedDefinitionsBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaProcessApiBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaSharedDefinitionsBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinProcessApiBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinSharedDefinitionsBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptProcessApiBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptSharedDefinitionsBuilder
import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph
import io.miragon.bpmn.application.port.outbound.GenerateApiCodePort
import io.miragon.bpmn.domain.BpmnModelApi
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.ProcessApiRequest
import io.miragon.bpmn.domain.SharedDefinitionsApi
import io.miragon.bpmn.domain.shared.OutputLanguage

internal class CodeGenerationAdapter(
    private val generators: Map<OutputLanguage, LanguageGenerator> = Companion.generators,
) : GenerateApiCodePort {

    override fun generate(request: ProcessApiRequest): List<GeneratedApiFile> {
        val processFiles = request.modelApis.flatMap { generateCode(it) }
        return processFiles + generateSharedCode(request.sharedDefinitionsApi)
    }

    fun generateCode(modelApi: BpmnModelApi): List<GeneratedApiFile> {
        val generator = generatorFor(modelApi.outputLanguage)
        val api = ProcessApiGraph.of(modelApi)
        val processFile = GeneratedApiFile(
            fileName = "${api.typeName.value}.${generator.fileExtension}",
            packagePath = api.packagePath.value,
            content = generator.processApiBuilder.render(api).value,
            language = modelApi.outputLanguage,
            processId = api.processId.value,
        )
        return listOf(processFile)
    }

    fun generateSharedCode(api: SharedDefinitionsApi): List<GeneratedApiFile> {
        val generator = generatorFor(api.outputLanguage)
        val graph = SharedDefinitionsGraph.of(api.definitions)
        return graph.groups.map { group ->
            GeneratedApiFile(
                fileName = "${group.type.typeName}.${generator.fileExtension}",
                packagePath = api.packagePath,
                content = generator.sharedDefinitionsBuilder.render(group, PackagePath(api.packagePath)).value,
                language = api.outputLanguage,
                processId = null,
            )
        }
    }

    private fun generatorFor(language: OutputLanguage): LanguageGenerator = requireNotNull(generators[language]) { "$language is not supported" }

    companion object {
        val generators: Map<OutputLanguage, LanguageGenerator> = mapOf(
            OutputLanguage.KOTLIN to LanguageGenerator(
                fileExtension = "kt",
                processApiBuilder = KotlinProcessApiBuilder(),
                sharedDefinitionsBuilder = KotlinSharedDefinitionsBuilder(),
            ),
            OutputLanguage.JAVA to LanguageGenerator(
                fileExtension = "java",
                processApiBuilder = JavaProcessApiBuilder(),
                sharedDefinitionsBuilder = JavaSharedDefinitionsBuilder(),
            ),
            OutputLanguage.CSHARP to LanguageGenerator(
                fileExtension = "cs",
                processApiBuilder = CSharpProcessApiBuilder(),
                sharedDefinitionsBuilder = CSharpSharedDefinitionsBuilder(),
            ),
            OutputLanguage.TYPESCRIPT to LanguageGenerator(
                fileExtension = "ts",
                processApiBuilder = TypeScriptProcessApiBuilder(),
                sharedDefinitionsBuilder = TypeScriptSharedDefinitionsBuilder(),
            ),
        )
    }
}
