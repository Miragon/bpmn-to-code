package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.poet

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.PackagePath
import io.miragon.bpmn.adapter.outbound.codegen.SourceCode
import io.miragon.bpmn.domain.GeneratedFileHeader

/**
 * Renders a top-level Kotlin object as the source text of a generated file named after it.
 */
internal object KotlinSourceFile {

    private val unusedSuppression = AnnotationSpec.builder(Suppress::class).addMember("%S", "unused").build()

    fun render(type: TypeSpec, packagePath: PackagePath): SourceCode {
        val objectName = requireNotNull(type.name)
        val fileSpec = FileSpec.builder(packagePath.value, objectName)
            .addFileComment(GeneratedFileHeader.COMMENT)
            .addType(type)
            .addAnnotation(unusedSuppression)
            .build()
        val source = buildString { fileSpec.writeTo(this) }
        return SourceCode(KotlinCodeFormat.withoutPublicModifiers(source))
    }
}
