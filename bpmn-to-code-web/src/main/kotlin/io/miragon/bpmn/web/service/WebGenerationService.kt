package io.miragon.bpmn.web.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.miragon.bpmn.adapter.inbound.CreateProcessApiInMemoryPlugin
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.web.model.BpmnFileData
import io.miragon.bpmn.web.model.GenerateRequest
import io.miragon.bpmn.web.model.GenerateResponse

class WebGenerationService(private val librarySourceProvider: LibrarySourceProvider = LibrarySourceProvider()) {

    private val logger = KotlinLogging.logger {}

    private val plugin = CreateProcessApiInMemoryPlugin()

    fun generate(request: GenerateRequest): GenerateResponse {
        val config = request.config
        logger.info { "Generating API for ${request.files.size} file(s) [${config.outputLanguage}, ${config.processEngine}]" }
        return GenerationGuard.run(files = request.files, failure = GenerateResponse::failure) {
            val bpmnInputs = request.files.map { this.buildCommand(it) }
            val generatedApiFiles = this.executePlugin(request.config, bpmnInputs)
            val generatedFiles = generatedApiFiles.map { mapToResponse(it) }
            val runsOnJvm = config.outputLanguage.runsOnJvm()
            GenerateResponse(
                success = true,
                files = generatedFiles,
                libraryFiles = if (runsOnJvm) librarySourceProvider.libraryFiles() else emptyList(),
                runtimeDependency = librarySourceProvider.runtimeDependency(config.outputLanguage),
            )
        }
    }

    private fun executePlugin(
        config: GenerateRequest.GenerationConfig,
        bpmnContents: List<CreateProcessApiInMemoryPlugin.BpmnInput>,
    ) = plugin.execute(
        bpmnContents = bpmnContents,
        packagePath = "com.example.process",
        outputLanguage = config.outputLanguage,
        engine = config.processEngine,
    )

    private fun buildCommand(file: BpmnFileData) = CreateProcessApiInMemoryPlugin.BpmnInput(bpmnXml = file.bpmnXml(), processName = file.processName())

    /**
     * The bundled sources are those of the JVM `bpmn-to-code-runtime`; C# has a runtime package of its own.
     */
    private fun OutputLanguage.runsOnJvm() = this != OutputLanguage.CSHARP

    private fun mapToResponse(apiFile: GeneratedApiFile) = GenerateResponse.GeneratedFile(
        fileName = apiFile.fileName,
        content = apiFile.content,
        processId = apiFile.processId,
    )
}
