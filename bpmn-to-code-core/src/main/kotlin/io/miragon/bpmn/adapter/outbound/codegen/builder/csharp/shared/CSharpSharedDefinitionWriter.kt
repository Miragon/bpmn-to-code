package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.shared

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpCodeFormat.disambiguated
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpCodeFormat.pascalCase
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpWriter
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedConstant
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.shared.VariableMapping

/**
 * Writes the C# class of one kind of shared definition: a member per definition plus an `All` list.
 */
internal abstract class CSharpSharedDefinitionWriter<T : Any> {

    abstract val type: SharedDefinitionType
    protected abstract val doc: String
    protected abstract val allElementType: String

    protected abstract fun definitionsOf(definitions: SharedDefinitions): List<VariableMapping<T>>

    protected abstract fun writeMembers(writer: CSharpWriter, definitions: List<VariableMapping<T>>)

    protected abstract fun allElement(memberReference: String): String

    fun shouldWrite(definitions: SharedDefinitions): Boolean = definitionsOf(definitions).isNotEmpty()

    fun write(writer: CSharpWriter, definitions: SharedDefinitions) {
        val ofKind = definitionsOf(definitions)
        writer.docComment(doc)
        writer.staticClass(type.typeName) {
            writeMembers(writer, ofKind)
            writer.line()
            writer.listProperty(name = "All", elementType = allElementType, elements = ofKind.map { allElement(memberReference(it.getName())) }, modifiers = "public static")
        }
    }

    /**
     * How a node refers to the shared constant of this kind.
     */
    fun reference(constant: SharedConstant): String = "${type.typeName}.${memberReference(constant.name)}"

    protected fun memberName(definition: VariableMapping<T>): String = pascalCase(definition.getName())

    private fun memberReference(constantName: String): String = disambiguated(pascalCase(constantName), type.typeName)
}
