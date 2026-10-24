package io.miragon.bpmn.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import io.miragon.bpmn.adapter.inbound.ValidateBpmnFilesystemPlugin
import io.miragon.bpmn.domain.validation.model.ValidationConfig

class ValidateCommand : CoreCliktCommand(name = "validate") {

    private val source by BpmnSourceOptions()
    private val failOnWarning by option("--failOnWarning", help = "Treat warnings as failures").flag()
    private val disabledRules by option("--disabledRules", help = "Comma-separated ids of rules to skip").split(",").default(emptyList())

    override fun help(context: Context) = "[Experimental] Validates every BPMN model without generating anything."

    override fun run() {
        val config = ValidationConfig(failOnWarning = failOnWarning, disabledRules = disabledRules.toSet())
        ValidateBpmnFilesystemPlugin().execute(
            baseDir = source.baseDir,
            filePattern = source.filePattern,
            engine = source.processEngine,
            validationConfig = config,
        )
        echo("BPMN validation passed")
    }
}
