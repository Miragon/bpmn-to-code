package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin

import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinSyntax.identifier
import io.miragon.bpmn.adapter.outbound.codegen.template.TemplateRenderer
import io.miragon.bpmn.adapter.outbound.codegen.template.TypeReferences

/**
 * Puts a rendered top-level object into its source file: header, package and the imports its references need. The
 * object is rendered first, so the imports are exactly the types it uses.
 */
internal object KotlinSourceFiles {

    fun render(packagePath: String, t: TypeReferences, body: String): String {
        val packageName = packagePath.split('.').joinToString(".") { identifier(it) }
        val file = KotlinTemplateModel.SourceFile(packageName = packageName, imports = t.imports, body = body)
        return TemplateRenderer.render("kotlin/source-file.ftl", file)
    }
}
