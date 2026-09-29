package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.CodeGenerationAdapter
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
internal class KotlinSharedDefinitionsBuilder : CodeGenerationAdapter.AbstractSharedDefinitionsBuilder() {

    private val writers = listOf(
        KotlinServiceTasksWriter,
        KotlinMessagesWriter,
        KotlinSignalsWriter,
        KotlinErrorsWriter,
        KotlinEscalationsWriter,
    )

    override fun buildApiFiles(api: SharedDefinitionsApi): List<GeneratedApiFile> = writers
        .filter { it.shouldWrite(api.definitions) }
        .map { toFile(it.write(api.definitions), api) }

    private fun toFile(type: TypeSpec, api: SharedDefinitionsApi): GeneratedApiFile {
        val objectName = requireNotNull(type.name)
        val unusedAnnotation = AnnotationSpec.builder(Suppress::class).addMember("%S", "unused").build()
        val fileSpec = FileSpec.builder(api.packagePath, objectName)
            .addFileComment(autoGenComment).addType(type).addAnnotation(unusedAnnotation).build()
        return GeneratedApiFile(
            fileName = "$objectName.kt",
            packagePath = api.packagePath,
            content = KotlinCodeFormat.withoutPublicModifiers(buildString { fileSpec.writeTo(this) }),
            language = api.outputLanguage,
            processId = null,
        )
    }
}
