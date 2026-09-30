package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionsBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.shared.JavaErrorsWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.shared.JavaEscalationsWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.shared.JavaMessagesWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.shared.JavaProcessVariablesWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.shared.JavaServiceTasksWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.shared.JavaSignalsWriter
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.SharedDefinitionsApi

/**
 * Generates one Java class per kind of engine-global identifier, shared by all Process APIs of a run.
 */
internal class JavaSharedDefinitionsBuilder : SharedDefinitionsBuilder {

    private val writers = listOf(
        JavaServiceTasksWriter,
        JavaMessagesWriter,
        JavaSignalsWriter,
        JavaErrorsWriter,
        JavaEscalationsWriter,
        JavaProcessVariablesWriter,
    )

    override fun buildApiFiles(api: SharedDefinitionsApi): List<GeneratedApiFile> = writers
        .filter { it.shouldWrite(api.definitions) }
        .map { JavaSourceFile.render(type = it.write(api.definitions), packagePath = api.packagePath) }
}
