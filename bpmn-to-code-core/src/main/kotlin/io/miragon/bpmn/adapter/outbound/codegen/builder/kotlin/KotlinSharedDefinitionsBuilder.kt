package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionsBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.poet.KotlinSourceFile
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.poet.shared.KotlinErrorsWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.poet.shared.KotlinEscalationsWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.poet.shared.KotlinMessagesWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.poet.shared.KotlinProcessVariablesWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.poet.shared.KotlinServiceTasksWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.poet.shared.KotlinSignalsWriter
import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph

/**
 * Renders the Kotlin object of one kind of engine-global identifier, shared by all Process APIs of a run.
 */
internal class KotlinSharedDefinitionsBuilder : SharedDefinitionsBuilder {

    private val writers = listOf(
        KotlinServiceTasksWriter,
        KotlinMessagesWriter,
        KotlinSignalsWriter,
        KotlinErrorsWriter,
        KotlinEscalationsWriter,
        KotlinProcessVariablesWriter,
    ).associateBy { it.type }

    override fun render(group: SharedDefinitionsGraph.Group, packagePath: String): String {
        val writer = writers.getValue(group.type)
        val type = writer.write(group.entries)
        return KotlinSourceFile.render(type = type, packagePath = packagePath)
    }
}
