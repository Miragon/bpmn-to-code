package io.miragon.bpmn.adapter

import io.miragon.bpmn.adapter.inbound.CreateProcessApiFilesystemPlugin
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.shared.OutputLanguage
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class GenerateBpmnModelsTask : AbstractBpmnTask() {

    @get:Input
    abstract val outputFolderPath: Property<String>

    @get:Input
    abstract val packagePath: Property<String>

    @get:Input
    abstract val outputLanguage: Property<OutputLanguage>

    init {
        GenerationCaching(this).enable(generatedDirectory = packageDirectory())
    }

    @TaskAction
    fun execute() {
        val service = CreateProcessApiFilesystemPlugin()
        val results = service.execute(
            baseDir = absolutePathOf(baseDir),
            filePattern = filePattern.get(),
            outputFolderPath = absolutePathOf(outputFolderPath),
            packagePath = packagePath.get(),
            outputLanguage = outputLanguage.get(),
            engine = processEngine.get(),
        )
        if (results.isEmpty()) {
            logger.lifecycle("No BPMN models found")
            return
        }
        results.forEach { result ->
            val files = result.sourceFiles.joinToString(", ")
            logger.lifecycle("  Generated: ${result.processId} (from $files)")
        }
        logger.lifecycle("BPMN models generated successfully (${results.size} models)")
    }

    private fun packageDirectory(): Provider<String> = outputFolderPath.zip(packagePath) { folder, packageName ->
        "$folder/${GeneratedApiFile.packageDirectoryOf(packageName)}"
    }
}
