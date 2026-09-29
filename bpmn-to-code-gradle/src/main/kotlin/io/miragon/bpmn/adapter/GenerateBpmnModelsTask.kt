package io.miragon.bpmn.adapter

import io.miragon.bpmn.adapter.inbound.CreateProcessApiFilesystemPlugin
import io.miragon.bpmn.domain.shared.OutputLanguage
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(
    because = "Task produces output based on files that can change at any time without the plugin knowing about it",
)
abstract class GenerateBpmnModelsTask : AbstractBpmnTask() {

    @Input
    lateinit var outputFolderPath: String

    @Input
    lateinit var packagePath: String

    @Input
    lateinit var outputLanguage: OutputLanguage

    @Input
    var enableVariants: Boolean = false

    @TaskAction
    fun execute() {
        val service = CreateProcessApiFilesystemPlugin()
        val results = service.execute(
            baseDir = baseDir,
            filePattern = filePattern,
            outputFolderPath = outputFolderPath,
            packagePath = packagePath,
            outputLanguage = outputLanguage,
            engine = processEngine,
            enableVariants = enableVariants,
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
