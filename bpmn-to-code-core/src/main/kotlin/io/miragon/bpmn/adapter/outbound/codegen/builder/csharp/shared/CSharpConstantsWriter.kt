package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.shared

import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpCodeFormat.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpWriter
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedValue
import io.miragon.bpmn.domain.shared.VariableMapping

/**
 * A kind of shared definition whose values are plain names, written as one `const string` each.
 */
internal abstract class CSharpConstantsWriter : CSharpSharedDefinitionWriter<String>() {

    override val allElementType = "string"

    override fun writeMembers(writer: CSharpWriter, definitions: List<VariableMapping<String>>) {
        definitions.forEach { writer.constant(memberName(it), it.getValue()) }
    }

    override fun allElement(memberReference: String): String = memberReference

    /**
     * A node's value of this kind: its shared constant, or the value itself when no shared constant holds it.
     */
    fun nodeValue(shared: SharedValue<String>): String {
        val constant = shared.constant
        return if (constant != null) reference(constant) else stringLiteral(shared.value)
    }
}
