package io.miragon.bpmn.adapter.outbound.codegen.builder.java.freemarker

import io.miragon.bpmn.adapter.outbound.codegen.PackagePath
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.ImportingSourceFile
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.RuntimeTypes
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.TypeReferences

/**
 * A generated Java file. A type named like one of the generated types is written fully qualified: next to a flow node
 * `class Event`, the runtime interface is `io.miragon.bpmn.runtime.Event`.
 */
internal object JavaSourceFile {

    private const val JAVA_LANG = "java.lang"

    private val QUALIFIED_NAMES = RuntimeTypes.qualifiedNames(listOf("VariableDefinitions")) + mapOf(
        "List" to "java.util.List",
        "String" to "$JAVA_LANG.String",
        "Override" to "$JAVA_LANG.Override",
    )

    fun of(packagePath: PackagePath, generatedTypeNames: Set<String>): ImportingSourceFile {
        val types = TypeReferences(
            qualifiedNames = QUALIFIED_NAMES,
            implicitPackages = setOf(JAVA_LANG),
            shadowingNames = generatedTypeNames,
        )
        return ImportingSourceFile(
            wrapperTemplate = "java/source-file.ftl",
            packageName = packagePath.value,
            types = types,
        )
    }
}
