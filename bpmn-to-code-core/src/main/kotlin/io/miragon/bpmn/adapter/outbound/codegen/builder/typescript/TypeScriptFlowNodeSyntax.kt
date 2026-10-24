package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptSyntax.identifier
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.MappingFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.VariableFacet
import io.miragon.bpmn.adapter.outbound.codegen.flownode.FlowNodeModel.NodeName
import io.miragon.bpmn.adapter.outbound.codegen.flownode.FlowNodeSyntax

/**
 * How a TypeScript flow node is written: an exported singleton class, e.g.
 * ```
 * export class ServiceTaskSendContract implements Runtime.FlowNode, Runtime.HasSuccessors, Runtime.HasJobType {
 *   static readonly INSTANCE = new ServiceTaskSendContract();
 *   readonly jobType = ServiceTasks.SEND_CONTRACT_DELEGATE;
 * ```
 * Runtime enums are string literal unions, so their members are literals. Runtime names and shared constants are
 * resolved through [file], which imports them.
 */
internal class TypeScriptFlowNodeSyntax(private val file: TypeScriptSourceFile) : FlowNodeSyntax {

    override fun literal(value: String): String = TypeScriptSyntax.stringLiteral(value)

    override fun enumMember(name: String): String = TypeScriptSyntax.stringLiteral(name)

    override fun nodeName(node: FlowEdge): NodeName = NodeName(
        type = identifier(node.objectName),
        property = identifier(node.propertyName),
    )

    /**
     * e.g. `Runtime.BoundaryEvent<ServiceTaskValidateApplication>, Runtime.HasSuccessors, Runtime.ErrorEvent`;
     * `HasSuccessors` and `FlowScope` mark what a process path can walk along.
     */
    override fun interfacesOf(node: FlowGraphNode): List<String> = buildList {
        add(nodeInterfaceOf(node))
        if (node.successors.isNotEmpty()) {
            add(file.runtimeType("HasSuccessors"))
        }
        if (node.interiorStarts.isNotEmpty()) {
            add(file.runtimeType("FlowScope"))
        }
        addAll(node.facets.facetInterfaces.map { file.runtimeType(it.typeName) })
    }

    override fun sharedReference(type: SharedDefinitionType, constantName: String): String = file.sharedReference(type, constantName)

    override fun variableName(variable: VariableFacet): String = identifier(variable.constantName)

    override fun mappingName(mapping: MappingFacet): String = identifier(mapping.constantName)

    private fun nodeInterfaceOf(node: FlowGraphNode): String {
        val host = node.facets.attachedTo
        if (node.isBoundaryEvent && host != null) {
            return "${file.runtimeType("BoundaryEvent")}<${identifier(host.objectName)}>"
        }
        if (node.eventType != null) {
            return file.runtimeType("Event")
        }
        return file.runtimeType("FlowNode")
    }
}
