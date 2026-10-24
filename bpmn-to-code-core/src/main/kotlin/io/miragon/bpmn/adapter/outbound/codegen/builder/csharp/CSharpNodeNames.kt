package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.freemarker.FlowNodeModel.NodeName
import io.miragon.bpmn.domain.ProcessApiNamingException

/**
 * How the flow nodes of one Process API are named in C#: reached through the property `FlowNodes.<Node>`, typed by
 * the class `<Node>Node`. The suffix keeps the property and its type apart, so no member of the node has to make way
 * for the type's name. Both are members of `FlowNodes`, so a type named like the property of another node, as for the
 * elements `task` and `taskNode`, fails generation.
 */
internal class CSharpNodeNames(objectNames: Collection<String>) {

    init {
        val clashingTypes = objectNames.map { typeName(it) }.filter { it in objectNames }
        if (clashingTypes.isNotEmpty()) {
            throw ProcessApiNamingException(
                "C# names the type of an element after the element with the suffix 'Node', which clashes with the elements $clashingTypes. Rename them.",
            )
        }
    }

    /**
     * e.g. property `ServiceTaskSendContract` and type `ServiceTaskSendContractNode`
     */
    fun of(objectName: String): NodeName = NodeName(type = typeName(objectName), property = objectName)

    private fun typeName(objectName: String): String = "${objectName}Node"
}
