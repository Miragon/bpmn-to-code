package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.writer.shared

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.writer.CSharpCodeFormat.disambiguated
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.writer.CSharpCodeFormat.pascalCase
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.writer.CSharpWriter
import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedConstant

/**
 * Writes the C# class of one kind of shared definition: a member per definition plus an `All` list.
 */
internal abstract class CSharpSharedDefinitionWriter {

    abstract val type: SharedDefinitionType
    protected abstract val doc: String
    protected abstract val allElementType: String

    protected abstract fun writeMembers(writer: CSharpWriter, entries: List<SharedDefinitionsGraph.Entry>)

    protected abstract fun allElement(memberReference: String): String

    fun write(writer: CSharpWriter, entries: List<SharedDefinitionsGraph.Entry>) {
        writer.docComment(doc)
        writer.staticClass(type.typeName) {
            writeMembers(writer, entries)
            writer.line()
            writer.staticListProperty(name = "All", elementType = allElementType, elements = entries.map { allElement(memberReference(it.constantName)) })
        }
    }

    /**
     * How a node refers to the shared constant of this kind.
     */
    fun reference(constant: SharedConstant): String = "${type.typeName}.${memberReference(constant.name)}"

    protected fun memberName(entry: SharedDefinitionsGraph.Entry): String = pascalCase(entry.constantName)

    private fun memberReference(constantName: String): String = disambiguated(pascalCase(constantName), type.typeName)
}
