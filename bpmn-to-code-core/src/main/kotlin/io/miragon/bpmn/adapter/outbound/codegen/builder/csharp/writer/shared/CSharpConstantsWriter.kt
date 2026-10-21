package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.writer.shared

import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpSyntax.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.writer.CSharpWriter
import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedValue

/**
 * A kind of shared definition whose values are plain names, written as one `const string` each.
 */
internal abstract class CSharpConstantsWriter : CSharpSharedDefinitionWriter() {

    override val allElementType = "string"

    override fun writeMembers(writer: CSharpWriter, entries: List<SharedDefinitionsGraph.Entry>) {
        entries.forEach { writer.constant(memberName(it), it.value) }
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
