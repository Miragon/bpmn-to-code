package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.shared

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptCodeFormat.propertyKey
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptWriter
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.shared.VariableMapping

internal class TypeScriptSharedDefinitionWriter<T : Any>(
    val type: SharedDefinitionType,
    private val doc: String,
    private val definitionsOf: (SharedDefinitions) -> List<VariableMapping<T>>,
    private val literalOf: (T) -> String,
    private val elementTypeOf: (TypeScriptWriter) -> String = { "string" },
) {

    fun shouldWrite(definitions: SharedDefinitions): Boolean = definitionsOf(definitions).isNotEmpty()

    fun write(writer: TypeScriptWriter, definitions: SharedDefinitions) {
        val entries = definitionsOf(definitions)
        writer.docComment(doc)
        writer.exportedConstant(type.typeName) {
            entries.forEach { definition -> writer.property(definition.getName(), literalOf(definition.getValue())) }
            val references = entries.map { "${type.typeName}.${propertyKey(it.getName())}" }
            writer.listGetter(key = "all", type = "readonly ${elementTypeOf(writer)}[]", elements = references)
        }
    }
}
