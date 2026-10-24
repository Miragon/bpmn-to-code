package io.miragon.bpmn.adapter.outbound.codegen.builder.jvm

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.MappingFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.VariableFacet
import io.miragon.bpmn.adapter.outbound.codegen.flownode.FlowNodeModel.NodeName
import io.miragon.bpmn.adapter.outbound.codegen.flownode.FlowNodeSyntax

/**
 * How a Kotlin or Java flow node is written: an object or class named after the element, reached through its
 * camelCase accessor. The shared types live in the Process API's package, so a reference to them needs no import.
 */
internal class JvmFlowNodeSyntax(types: TypeReferences, private val syntax: JvmSyntax) : FlowNodeSyntax {

    private val supertypes = JvmSupertypes(types, syntax)

    override fun literal(value: String): String = syntax.stringLiteral(value)

    override fun enumMember(name: String): String = name

    override fun nodeName(node: FlowEdge): NodeName = NodeName(
        type = syntax.identifier(node.objectName),
        property = syntax.identifier(node.propertyName),
    )

    override fun interfacesOf(node: FlowGraphNode): List<String> = supertypes.of(node)

    override fun sharedReference(type: SharedDefinitionType, constantName: String): String {
        val memberName = syntax.identifier(constantName)
        return "${type.typeName}.$memberName"
    }

    override fun variableName(variable: VariableFacet): String = syntax.identifier(variable.constantName)

    override fun mappingName(mapping: MappingFacet): String = syntax.identifier(mapping.constantName)
}
