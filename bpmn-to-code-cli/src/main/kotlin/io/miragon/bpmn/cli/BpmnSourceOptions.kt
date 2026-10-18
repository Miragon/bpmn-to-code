package io.miragon.bpmn.cli

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import io.miragon.bpmn.domain.shared.ProcessEngine

class BpmnSourceOptions : OptionGroup() {
    val baseDir by option("--baseDir", help = "Directory the file pattern is resolved against").required()
    val filePattern by option("--filePattern", help = "Glob of the BPMN files, relative to baseDir").required()
    val processEngine by option("--processEngine", help = "Engine the models are written for").enum<ProcessEngine>().required()
}
