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

    init {
        GenerationCaching(this).enable(generatedDirectory = outputFolderPath)
    }

    @TaskAction
    fun execute() {
        logger.warn(
            "[DEPRECATED] The 'generateBpmnModelJson' task is deprecated and will be removed in 7.0. " +
                "If you use the Process JSON, describe your use case in $FEEDBACK_ISSUE.",
        )
        val plugin = CreateProcessJsonFilesystemPlugin()
        plugin.execute(
            baseDir = absolutePathOf(baseDir),
            filePattern = filePattern.get(),
            outputFolderPath = absolutePathOf(outputFolderPath),
            engine = processEngine.get(),
        )
        logger.lifecycle("BPMN JSON files generated successfully")
    }

    private companion object {
        const val FEEDBACK_ISSUE = "https://github.com/Miragon/bpmn-to-code/issues/206"
    }
}
