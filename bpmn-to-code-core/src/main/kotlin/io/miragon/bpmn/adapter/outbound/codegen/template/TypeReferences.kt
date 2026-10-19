package io.miragon.bpmn.adapter.outbound.codegen.template

import freemarker.template.SimpleScalar
import freemarker.template.TemplateHashModel
import freemarker.template.TemplateModel

/**
 * The type references of one generated file, and the imports they need.
 *
 * A type is referenced by its simple name and imported, unless a type generated into the same file has that simple
 * name and would shadow it; then it is written fully qualified and not imported. Types of [implicitPackages] are never
 * imported. Templates read references as a hash (`${t.SequenceFlows}`), so every reference a template writes is
 * recorded, and [imports] lists exactly the types the file uses.
 */
internal class TypeReferences(
    private val qualifiedNames: Map<String, String>,
    private val implicitPackages: Set<String>,
    private val shadowingNames: Set<String>,
) : TemplateHashModel {

    private val usedImports = sortedSetOf<String>()

    val imports: List<String>
        get() = usedImports.toList()

    fun of(simpleName: String): String {
        val qualifiedName = requireNotNull(qualifiedNames[simpleName]) { "unknown type $simpleName" }
        if (simpleName in shadowingNames) return qualifiedName
        if (qualifiedName.substringBeforeLast('.') !in implicitPackages) usedImports += qualifiedName
        return simpleName
    }

    override fun get(key: String): TemplateModel = SimpleScalar(of(key))

    override fun isEmpty(): Boolean = false
}
