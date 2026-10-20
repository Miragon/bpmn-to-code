package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionsBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.poet.JavaSourceFile
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.poet.shared.JavaErrorsWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.poet.shared.JavaEscalationsWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.poet.shared.JavaMessagesWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.poet.shared.JavaProcessVariablesWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.poet.shared.JavaServiceTasksWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.poet.shared.JavaSignalsWriter
import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph

/**
 * Renders the Java class of one kind of engine-global identifier, shared by all Process APIs of a run.
 */
internal class JavaSharedDefinitionsBuilder : SharedDefinitionsBuilder {

    private val writers = listOf(
        JavaServiceTasksWriter,
        JavaMessagesWriter,
        JavaSignalsWriter,
        JavaErrorsWriter,
        JavaEscalationsWriter,
        JavaProcessVariablesWriter,
    ).associateBy { it.type }

    override fun render(group: SharedDefinitionsGraph.Group, packagePath: String): String {
        val writer = writers.getValue(group.type)
        val type = writer.write(group.entries)
        return JavaSourceFile.render(type = type, packagePath = packagePath)
    }
}
