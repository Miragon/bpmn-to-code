package io.miragon.bpmn.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.parameters.options.versionOption

class BpmnToCodeCommand : CoreCliktCommand(name = "bpmn-to-code") {

    init {
        val version = javaClass.`package`.implementationVersion ?: "development"
        versionOption(version = version, names = setOf("--version"))
        context {
            echoMessage = { _, message, trailingNewline, err ->
                val stream = if (err) System.err else System.out
                if (trailingNewline) stream.println(message) else stream.print(message)
            }
        }
    }

    override fun help(context: Context) = "Generates type-safe APIs from BPMN models and validates them."

    override fun run() = Unit
}
