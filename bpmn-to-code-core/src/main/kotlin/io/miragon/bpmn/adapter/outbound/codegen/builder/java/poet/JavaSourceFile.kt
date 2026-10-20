package io.miragon.bpmn.adapter.outbound.codegen.builder.java.poet

import com.palantir.javapoet.JavaFile
import com.palantir.javapoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.PackagePath
import io.miragon.bpmn.adapter.outbound.codegen.SourceCode
import io.miragon.bpmn.domain.GeneratedFileHeader

/**
 * Renders a top-level Java class as the source text of a generated file named after it.
 */
internal object JavaSourceFile {

    fun render(type: TypeSpec, packagePath: PackagePath): SourceCode {
        val javaFile = JavaFile.builder(packagePath.value, type)
            .skipJavaLangImports(true)
            .addFileComment(GeneratedFileHeader.COMMENT)
            .build()
        return SourceCode(buildString { javaFile.writeTo(this) })
    }
}
