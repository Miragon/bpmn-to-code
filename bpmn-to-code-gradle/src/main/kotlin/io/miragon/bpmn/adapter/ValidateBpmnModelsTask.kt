package io.miragon.bpmn.adapter

import io.miragon.bpmn.adapter.inbound.ValidateBpmnFilesystemPlugin
import io.miragon.bpmn.domain.validation.model.ValidationConfig
import org.gradle.api.GradleException
import org.gradle.api.Incubating
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@Incubating
@DisableCachingByDefault(
    because = "Validation depends on BPMN files that can change at any time without the plugin knowing about it",
)
abstract class ValidateBpmnModelsTask : AbstractBpmnTask() {

    @Input
    var failOnWarning: Boolean = false

    @Input
    var disabledRules: Set<String> = emptySet()

    @TaskAction
    fun execute() {
        logger.warn("[EXPERIMENTAL] The 'validateBpmnModels' task is experimental and may change in future releases.")
        val plugin = ValidateBpmnFilesystemPlugin()
        val config = ValidationConfig(failOnWarning = failOnWarning, disabledRules = disabledRules)
        val result = plugin.execute(
            baseDir = baseDir,
            filePattern = filePattern,
            engine = processEngine,
            validationConfig = config,
        )

        result.warnings.forEach { logger.warn(it.describe()) }
        result.errors.forEach { logger.error(it.describe()) }

        if (result.hasFailures(failOnWarning)) {
            throw GradleException(result.failureSummary)
        }
        logger.lifecycle("BPMN validation passed")
    }
}
