package io.miragon.bpmn.adapter

import io.miragon.bpmn.adapter.inbound.CreateProcessApiFilesystemPlugin
import io.miragon.bpmn.domain.shared.OutputLanguage
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(
    because = "Task produces output based on files that can change at any time without the plugin knowing about it",
)
abstract class GenerateBpmnModelsTask : AbstractBpmnTask() {

    @get:Input
    abstract val outputFolderPath: Property<String>

    @get:Input
    abstract val packagePath: Property<String>

    @get:Input
    abstract val outputLanguage: Property<OutputLanguage>

    @get:Input
    abstract val enableVariants: Property<Boolean>

    init {
        enableVariants.convention(false)
    }

    @TaskAction
    fun execute() {
        val service = CreateProcessApiFilesystemPlugin()
        val results = service.execute(
            baseDir = baseDir.get(),
            filePattern = filePattern.get(),
            outputFolderPath = outputFolderPath.get(),
            packagePath = packagePath.get(),
            outputLanguage = outputLanguage.get(),
            engine = processEngine.get(),
            enableVariants = enableVariants.get(),
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
}
