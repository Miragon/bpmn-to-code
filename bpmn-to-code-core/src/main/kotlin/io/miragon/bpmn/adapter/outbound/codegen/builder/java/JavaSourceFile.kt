package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import com.palantir.javapoet.JavaFile
import com.palantir.javapoet.TypeSpec
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.GeneratedFileHeader
import io.miragon.bpmn.domain.shared.OutputLanguage

/**
 * Renders a top-level Java class as a generated source file named after it.
 */
internal object JavaSourceFile {

    fun render(type: TypeSpec, packagePath: String, processId: String? = null): GeneratedApiFile {
        val javaFile = JavaFile.builder(packagePath, type)
            .skipJavaLangImports(true)
            .addFileComment(GeneratedFileHeader.COMMENT)
            .build()
        return GeneratedApiFile(
            fileName = "${type.name()}.java",
            packagePath = packagePath,
            content = buildString { javaFile.writeTo(this) },
            language = OutputLanguage.JAVA,
            processId = processId,
        )
    }
}
