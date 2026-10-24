package io.miragon.bpmn.adapter.outbound.codegen.builder.jvm

import io.miragon.bpmn.adapter.outbound.codegen.rendering.TemplateNames

/**
 * The type references of one generated file, and the imports they need.
 *
 * A type is referenced by its simple name and imported, unless a type generated into the same file has that simple
 * name and would shadow it; then it is written fully qualified and not imported. Types of [implicitPackages] are never
 * imported. Templates read references as a hash (`${types.SequenceFlows}`), so every reference a template writes is
 * recorded through [names], and [imports] lists exactly the types the file uses.
 */
internal class TypeReferences(
    private val qualifiedNames: Map<String, String>,
    private val implicitPackages: Set<String>,
    private val shadowingNames: Set<String>,
) {

    private val usedImports = sortedSetOf<String>()

    val names = TemplateNames(::of)

    val imports: List<String>
        get() = usedImports.toList()

    fun of(simpleName: String): String {
        val qualifiedName = requireNotNull(qualifiedNames[simpleName]) { "unknown type $simpleName" }
        if (simpleName in shadowingNames) return qualifiedName
        if (qualifiedName.substringBeforeLast('.') !in implicitPackages) usedImports += qualifiedName
        return simpleName
    }
}
