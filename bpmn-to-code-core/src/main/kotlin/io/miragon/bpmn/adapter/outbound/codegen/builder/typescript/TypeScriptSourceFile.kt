package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptImports.RuntimeUsage
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.GeneratedFileHeader
import io.miragon.bpmn.domain.shared.OutputLanguage

internal object TypeScriptSourceFile {

    fun render(
        typeName: String,
        packagePath: String,
        processId: String? = null,
        writeBody: (TypeScriptWriter) -> Unit,
    ): GeneratedApiFile {
        val body = TypeScriptWriter()
        writeBody(body)
        val content = buildString {
            append(GeneratedFileHeader.LINE).append('\n')
            runtimeImportOf(body.imports.runtime)?.let { append(it).append('\n') }
            body.imports.sharedDefinitions.forEach { type -> append("import { $type } from \"./$type.js\";\n") }
            append('\n')
            append(body.render())
        }
        return GeneratedApiFile(
            fileName = "$typeName.ts",
            packagePath = packagePath,
            content = content,
            language = OutputLanguage.TYPESCRIPT,
            processId = processId,
        )
    }

    private fun runtimeImportOf(usage: RuntimeUsage): String? {
        val alias = TypeScriptRuntimeTypes.ALIAS
        val module = TypeScriptRuntimeTypes.MODULE
        return when (usage) {
            RuntimeUsage.NONE -> null
            RuntimeUsage.TYPE -> "import type * as $alias from \"$module\";"
            RuntimeUsage.VALUE -> "import * as $alias from \"$module\";"
        }
    }
}
