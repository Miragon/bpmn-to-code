package io.miragon.bpmn.adapter

import io.miragon.bpmn.adapter.inbound.CreateProcessJsonFilesystemPlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class GenerateBpmnJsonTask : AbstractBpmnTask() {

    @get:Input
    abstract val outputFolderPath: Property<String>

    @get:Input
    abstract val enableVariants: Property<Boolean>

    init {
        enableVariants.convention(false)
        GenerationCaching(this).enable(generatedDirectory = outputFolderPath)
    }

    @TaskAction
    fun execute() {
        val plugin = CreateProcessJsonFilesystemPlugin()
        plugin.execute(
            baseDir = absolutePathOf(baseDir),
            filePattern = filePattern.get(),
            outputFolderPath = absolutePathOf(outputFolderPath),
            engine = processEngine.get(),
            enableVariants = enableVariants.get(),
        )
        logger.lifecycle("BPMN JSON files generated successfully")
    }
}
