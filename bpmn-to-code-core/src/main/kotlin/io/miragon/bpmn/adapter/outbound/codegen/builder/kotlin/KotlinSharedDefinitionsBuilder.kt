package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionsBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.shared.KotlinErrorsWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.shared.KotlinEscalationsWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.shared.KotlinMessagesWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.shared.KotlinServiceTasksWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.shared.KotlinSignalsWriter
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.SharedDefinitionsApi

/**
 * Generates one Kotlin object per kind of engine-global identifier, shared by all Process APIs of a run.
 */
internal class KotlinSharedDefinitionsBuilder : SharedDefinitionsBuilder {

    private val writers = listOf(
        KotlinServiceTasksWriter,
        KotlinMessagesWriter,
        KotlinSignalsWriter,
        KotlinErrorsWriter,
        KotlinEscalationsWriter,
    )

    override fun buildApiFiles(api: SharedDefinitionsApi): List<GeneratedApiFile> = writers
        .filter { it.shouldWrite(api.definitions) }
        .map { KotlinSourceFile.render(type = it.write(api.definitions), packagePath = api.packagePath) }
}
