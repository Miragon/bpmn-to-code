package io.miragon.bpmn.adapter

import io.miragon.bpmn.adapter.inbound.CreateProcessJsonFilesystemPlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(
    because = "Task produces output based on files that can change at any time without the plugin knowing about it",
)
abstract class GenerateBpmnJsonTask : AbstractBpmnTask() {

    @get:Input
    abstract val outputFolderPath: Property<String>

    @get:Input
    abstract val enableVariants: Property<Boolean>

    init {
        enableVariants.convention(false)
    }

    @TaskAction
    fun execute() {
        val plugin = CreateProcessJsonFilesystemPlugin()
        plugin.execute(
            baseDir = baseDir.get(),
            filePattern = filePattern.get(),
            outputFolderPath = outputFolderPath.get(),
            engine = processEngine.get(),
            enableVariants = enableVariants.get(),
        )
        logger.lifecycle("BPMN JSON files generated successfully")
    }
}
