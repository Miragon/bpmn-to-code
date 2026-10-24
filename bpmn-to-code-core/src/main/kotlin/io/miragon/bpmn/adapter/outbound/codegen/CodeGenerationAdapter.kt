package io.miragon.bpmn.adapter.outbound.codegen

import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpProcessApiBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpSharedDefinitionsBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.jvm.JvmLanguage
import io.miragon.bpmn.adapter.outbound.codegen.builder.jvm.JvmProcessApiBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.jvm.JvmSharedDefinitionsBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptProcessApiBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptSharedDefinitionsBuilder
import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph
import io.miragon.bpmn.application.port.outbound.GenerateApiCodePort
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.ProcessApiRequest
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.shared.OutputLanguage

internal class CodeGenerationAdapter(
    private val generators: Map<OutputLanguage, LanguageGenerator> = Companion.generators,
) : GenerateApiCodePort {

    override fun generate(request: ProcessApiRequest): List<GeneratedApiFile> {
        val processFiles = generateCode(request)
        val sharedDefinitions = SharedDefinitions.from(request.models)
        val sharedFiles = generateSharedCode(
            definitions = sharedDefinitions,
            outputLanguage = request.outputLanguage,
            packagePath = request.packagePath,
        )
        return processFiles + sharedFiles
    }

    fun generateCode(request: ProcessApiRequest): List<GeneratedApiFile> {
        val generator = generatorFor(request.outputLanguage)
        return request.models.map { model ->
            val api = ProcessApiGraph.of(model, request)
            GeneratedApiFile(
                fileName = "${api.typeName}.${generator.fileExtension}",
                packagePath = api.packagePath.value,
                content = generator.processApiBuilder.render(api).value,
                language = request.outputLanguage,
                processId = api.processId,
            )
        }
    }

    fun generateSharedCode(definitions: SharedDefinitions, outputLanguage: OutputLanguage, packagePath: String): List<GeneratedApiFile> {
        val generator = generatorFor(outputLanguage)
        val graph = SharedDefinitionsGraph.of(definitions)
        return graph.groups.map { group ->
            GeneratedApiFile(
                fileName = "${group.type.typeName}.${generator.fileExtension}",
                packagePath = packagePath,
                content = generator.sharedDefinitionsBuilder.render(group, PackagePath(packagePath)).value,
                language = outputLanguage,
                processId = null,
            )
        }
    }

    private fun generatorFor(language: OutputLanguage): LanguageGenerator = requireNotNull(generators[language]) { "$language is not supported" }

    companion object {
        val generators: Map<OutputLanguage, LanguageGenerator> = mapOf(
            OutputLanguage.KOTLIN to LanguageGenerator(
                fileExtension = "kt",
                processApiBuilder = JvmProcessApiBuilder(JvmLanguage.KOTLIN),
                sharedDefinitionsBuilder = JvmSharedDefinitionsBuilder(JvmLanguage.KOTLIN),
            ),
            OutputLanguage.JAVA to LanguageGenerator(
                fileExtension = "java",
                processApiBuilder = JvmProcessApiBuilder(JvmLanguage.JAVA),
                sharedDefinitionsBuilder = JvmSharedDefinitionsBuilder(JvmLanguage.JAVA),
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
