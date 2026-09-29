package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import com.palantir.javapoet.JavaFile
import com.palantir.javapoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.CodeGenerationAdapter
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.shared.JavaErrorsWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.shared.JavaEscalationsWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.shared.JavaMessagesWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.shared.JavaServiceTasksWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.shared.JavaSignalsWriter
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.SharedDefinitionsApi

/**
 * Generates one Java class per kind of engine-global identifier, shared by all Process APIs of a run.
 */
internal class JavaSharedDefinitionsBuilder : CodeGenerationAdapter.AbstractSharedDefinitionsBuilder() {

    private val writers = listOf(
        JavaServiceTasksWriter,
        JavaMessagesWriter,
        JavaSignalsWriter,
        JavaErrorsWriter,
        JavaEscalationsWriter,
    )

    override fun buildApiFiles(api: SharedDefinitionsApi): List<GeneratedApiFile> = writers
        .filter { it.shouldWrite(api.definitions) }
        .map { toFile(it.write(api.definitions), api) }

    private fun toFile(type: TypeSpec, api: SharedDefinitionsApi): GeneratedApiFile {
        val javaFile = JavaFile.builder(api.packagePath, type).skipJavaLangImports(true).addFileComment(autoGenComment).build()
        return GeneratedApiFile(
            fileName = "${type.name()}.java",
            packagePath = api.packagePath,
            content = buildString { javaFile.writeTo(this) },
            language = api.outputLanguage,
            processId = null,
        )
    }
}
