package io.miragon.bpmn.cli

import kotlin.system.exitProcess

fun main(args: Array<String>) {
    System.setProperty("kotlin-logging.logStartupMessage", "false")
    exitProcess(BpmnToCodeCli.execute(args))
}
