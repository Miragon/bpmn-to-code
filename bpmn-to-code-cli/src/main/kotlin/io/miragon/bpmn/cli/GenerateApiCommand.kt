package io.miragon.bpmn.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import io.miragon.bpmn.adapter.inbound.CreateProcessApiFilesystemPlugin
import io.miragon.bpmn.domain.shared.OutputLanguage

class GenerateApiCommand : CoreCliktCommand(name = "generate-api") {

    private val source by BpmnSourceOptions()
    private val outputFolderPath by option("--outputFolderPath", help = "Source root the code is written to").required()
    private val packagePath by option(
        "--packagePath",
        help = "Package of the generated code, the namespace for C#; for TypeScript only an optional directory below outputFolderPath",
    ).default("")
    private val outputLanguage by option("--outputLanguage", help = "Language of the generated code").enum<OutputLanguage>().required()

    override fun help(context: Context) = "Generates the Process API of every BPMN model and the shared definitions."

    override fun run() {
        if (packagePath.isBlank() && outputLanguage != OutputLanguage.TYPESCRIPT) {
            throw UsageError("--packagePath is required for $outputLanguage")
        }
        val results = CreateProcessApiFilesystemPlugin().execute(
            baseDir = source.baseDir,
            filePattern = source.filePattern,
            outputFolderPath = outputFolderPath,
            packagePath = packagePath,
            outputLanguage = outputLanguage,
            engine = source.processEngine,
        )
        if (results.isEmpty()) {
            echo("No BPMN models found")
            return
        }
        results.forEach { result ->
            val files = result.sourceFiles.joinToString(", ")
            echo("Generated: ${result.processId} (from $files)")
        }
    }
}
