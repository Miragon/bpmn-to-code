package io.miragon.bpmn.adapter

import io.miragon.bpmn.adapter.inbound.ValidateBpmnFilesystemPlugin
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.validation.model.ValidationConfig
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Incubating
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@Incubating
@DisableCachingByDefault(
    because = "Validation depends on BPMN files that can change at any time without the plugin knowing about it",
)
abstract class ValidateBpmnModelsTask : DefaultTask() {

    @Input
    lateinit var baseDir: String

    @Input
    lateinit var filePattern: String

    @Input
    lateinit var processEngine: ProcessEngine

    @Input
    var failOnWarning: Boolean = false

    @Input
    var disabledRules: Set<String> = emptySet()

    @TaskAction
    fun execute() {
        validate()
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

    private fun validate() {
        check(this::baseDir.isInitialized) { "baseDir must be configured in bpmnToCode { ... }" }
        check(this::filePattern.isInitialized) { "filePattern must be configured in bpmnToCode { ... }" }
        check(this::processEngine.isInitialized) { "processEngine must be configured in bpmnToCode { ... }" }
    }
}
