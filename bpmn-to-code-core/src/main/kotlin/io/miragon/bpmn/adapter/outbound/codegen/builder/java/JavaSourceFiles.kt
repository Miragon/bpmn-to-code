package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import io.miragon.bpmn.adapter.outbound.codegen.template.TemplateRenderer
import io.miragon.bpmn.adapter.outbound.codegen.template.TypeReferences

/**
 * Puts a rendered top-level class into its source file: header, package and the imports its references need. The
 * class is rendered first, so the imports are exactly the types it uses.
 */
internal object JavaSourceFiles {

    fun render(packageName: String, t: TypeReferences, body: String): String {
        val file = JavaTemplateModel.SourceFile(packageName = packageName, imports = t.imports, body = body)
        return TemplateRenderer.render("java/source-file.ftl", file)
    }
}
