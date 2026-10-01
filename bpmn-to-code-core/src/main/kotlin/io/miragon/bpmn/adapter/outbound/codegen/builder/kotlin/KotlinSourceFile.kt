package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.GeneratedFileHeader
import io.miragon.bpmn.domain.shared.OutputLanguage

/**
 * Renders a top-level Kotlin object as a generated source file named after it.
 */
internal object KotlinSourceFile {

    private val unusedSuppression = AnnotationSpec.builder(Suppress::class).addMember("%S", "unused").build()

    fun render(type: TypeSpec, packagePath: String, processId: String? = null): GeneratedApiFile {
        val objectName = requireNotNull(type.name)
        val fileSpec = FileSpec.builder(packagePath, objectName)
            .addFileComment(GeneratedFileHeader.COMMENT)
            .addType(type)
            .addAnnotation(unusedSuppression)
            .build()
        return GeneratedApiFile(
            fileName = "$objectName.kt",
            packagePath = packagePath,
            content = KotlinCodeFormat.withoutPublicModifiers(buildString { fileSpec.writeTo(this) }),
            language = OutputLanguage.KOTLIN,
            processId = processId,
        )
    }
}
