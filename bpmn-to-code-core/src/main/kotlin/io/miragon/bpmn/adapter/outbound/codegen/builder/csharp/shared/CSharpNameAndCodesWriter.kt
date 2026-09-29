package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.shared

import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpCodeFormat.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpWriter
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedValue
import io.miragon.bpmn.domain.shared.VariableMapping

/**
 * A kind of shared definition whose values carry a name and a code, written as one nested class each holding a
 * `Reference` and a `Code` constant.
 */
internal abstract class CSharpNameAndCodesWriter : CSharpSharedDefinitionWriter<Pair<String, String>>() {

    override val allElementType = "(string Reference, string Code)"

    override fun writeMembers(writer: CSharpWriter, definitions: List<VariableMapping<Pair<String, String>>>) {
        writer.forEachSeparated(definitions) { definition ->
            val (name, code) = definition.getValue()
            writer.staticClass(memberName(definition)) {
                writer.constant("Reference", name)
                writer.constant("Code", code)
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
