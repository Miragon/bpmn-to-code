package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpSyntax.disambiguated
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.FlowNodeModel.NodeName

/**
 * How the flow nodes of one Process API are named in C#: reached through the property `FlowNodes.<Node>`, typed by
 * the class `<Node>Node`. The suffix keeps the property and its type apart, so no member of the node has to make way
 * for the type's name. Both are members of `FlowNodes`, so a type named like the property of another node, as for the
 * elements `task` and `taskNode`, gets a trailing underscore.
 */
internal class CSharpNodeNames(objectNames: Collection<String>) {

    private val propertyNames = objectNames.map { propertyName(it) }.toSet()

    /**
     * e.g. property `ServiceTaskSendContract` and type `ServiceTaskSendContractNode`
     */
    fun of(objectName: String): NodeName = NodeName(type = typeName(objectName), property = propertyName(objectName))

    /**
     * Renamed only when it would equal `FlowNodes` itself.
     */
    private fun propertyName(objectName: String): String = disambiguated(objectName, FLOW_NODES)

    private fun typeName(objectName: String): String {
        val typeName = "${objectName}Node"
        if (typeName !in propertyNames) return typeName
        return typeName + "_"
    }

    private companion object {
        const val FLOW_NODES = "FlowNodes"
    }
}
