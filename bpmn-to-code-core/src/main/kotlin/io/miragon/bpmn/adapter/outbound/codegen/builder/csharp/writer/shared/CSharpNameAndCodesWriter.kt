package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.writer.shared

import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.writer.CSharpCodeFormat.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.writer.CSharpWriter
import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedValue

/**
 * A kind of shared definition whose values carry a name and a code, written as one nested class each holding a
 * `Reference` and a `Code` constant.
 */
internal abstract class CSharpNameAndCodesWriter : CSharpSharedDefinitionWriter() {

    override val allElementType = "(string Reference, string Code)"

    override fun writeMembers(writer: CSharpWriter, entries: List<SharedDefinitionsGraph.Entry>) {
        writer.forEachSeparated(entries) { entry ->
            writer.staticClass(memberName(entry)) {
                writer.constant("Reference", entry.value)
                writer.constant("Code", checkNotNull(entry.code))
            }
        }
    }

    override fun allElement(memberReference: String): String = "($memberReference.Reference, $memberReference.Code)"

    /**
     * A node's name and code of this kind as constructor arguments: its shared constant's, or the values themselves
     * when no shared constant holds them.
     */
    fun nodeArguments(shared: SharedValue<Pair<String, String>>): String {
        val constant = shared.constant
        if (constant != null) {
            val reference = reference(constant)
            return "$reference.Reference, $reference.Code"
        }
        val (name, code) = shared.value
        return "${stringLiteral(name)}, ${stringLiteral(code)}"
    }
}
