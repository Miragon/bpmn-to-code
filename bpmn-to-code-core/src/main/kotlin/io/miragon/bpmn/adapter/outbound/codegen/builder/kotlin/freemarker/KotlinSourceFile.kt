package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.freemarker

import io.miragon.bpmn.adapter.outbound.codegen.PackagePath
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.freemarker.KotlinSyntax.identifier
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.ImportingSourceFile
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.RuntimeTypes
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.TypeReferences

/**
 * A generated Kotlin file. A type named like one of the generated types is written fully qualified: next to a flow node
 * `object Event`, the runtime interface is `io.miragon.bpmn.runtime.Event`.
 */
internal object KotlinSourceFile {

    private val QUALIFIED_NAMES = RuntimeTypes.qualifiedNames(listOf("RegisteredVariableDefinitions")) + mapOf(
        "Boolean" to "kotlin.Boolean",
        "String" to "kotlin.String",
        "List" to "kotlin.collections.List",
    )

    fun of(packagePath: PackagePath, generatedTypeNames: Set<String>): ImportingSourceFile {
        val types = TypeReferences(
            qualifiedNames = QUALIFIED_NAMES,
            implicitPackages = setOf("kotlin", "kotlin.collections"),
            shadowingNames = generatedTypeNames,
        )
        val packageName = packagePath.value.split('.').joinToString(".") { identifier(it) }
        return ImportingSourceFile(wrapperTemplate = "kotlin/source-file.ftl", packageName = packageName, types = types)
    }
}
