package io.miragon.bpmn.adapter

import io.miragon.bpmn.adapter.inbound.CreateProcessJsonFilesystemPlugin
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(
    because = "Task produces output based on files that can change at any time without the plugin knowing about it",
)
abstract class GenerateBpmnJsonTask : AbstractBpmnTask() {

    @Input
    lateinit var outputFolderPath: String

    @Input
    var enableVariants: Boolean = false

    @TaskAction
    fun execute() {
        val plugin = CreateProcessJsonFilesystemPlugin()
        plugin.execute(
            baseDir = baseDir,
            filePattern = filePattern,
            outputFolderPath = outputFolderPath,
            engine = processEngine,
            enableVariants = enableVariants,
        )
        logger.lifecycle("BPMN JSON files generated successfully")
    }
}
