package io.miragon.bpmn.cli

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.subcommands

object BpmnToCodeCli {

    fun execute(args: Array<String>): Int {
        val command = BpmnToCodeCommand().subcommands(GenerateApiCommand(), ValidateCommand())
        return try {
            command.parse(args)
            ExitCode.SUCCESS
        } catch (error: CliktError) {
            command.echoFormattedHelp(error)
            exitCodeOf(error)
        } catch (failure: RuntimeException) {
            reportFailure(failure)
        }
    }

    private fun exitCodeOf(error: CliktError): Int {
        val isUsageError = error is UsageError || (error is PrintHelpMessage && error.error)
        return if (isUsageError) ExitCode.USAGE_ERROR else error.statusCode
    }

    private fun reportFailure(failure: RuntimeException): Int {
        val message = failure.message.orEmpty()
        val causeMessage = failure.cause?.message?.takeUnless { causeMessage -> causeMessage in message }
        listOfNotNull(message, causeMessage).forEach { line -> System.err.println(line) }
        return ExitCode.FAILURE
    }
}
