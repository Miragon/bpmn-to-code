package io.miragon.bpmn.adapter.outbound.codegen.flownode

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.MappingFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedValue
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.VariableFacet
import io.miragon.bpmn.adapter.outbound.codegen.flownode.FlowNodeModel.NodeName

/**
 * What a [FlowNodeMapper] needs to know about one output language: how values, names and references are written.
 */
internal interface FlowNodeSyntax {

    fun literal(value: String): String

    /**
     * A member of a runtime enum, e.g. `SERVICE_TASK` for the element type `SERVICE_TASK`
     */
    fun enumMember(name: String): String

    fun nodeName(node: FlowEdge): NodeName

    fun interfacesOf(node: FlowGraphNode): List<String>

    /**
     * e.g. `Messages.MIRAVELO_CONTRACT_SIGNED`
     */
    fun sharedReference(type: SharedDefinitionType, constantName: String): String

    fun variableName(variable: VariableFacet): String

    fun mappingName(mapping: MappingFacet): String

    /**
     * e.g. `Errors.MIRAVELO_APPLICATION_INVALID`, or name and code as literals when no shared constant holds them
     */
    fun sharedNameAndCode(type: SharedDefinitionType, sharedValue: SharedValue<Pair<String, String>>): SharedNameAndCode {
        val (name, code) = sharedValue.value
        return SharedNameAndCode(
            reference = sharedValue.constant?.let { sharedReference(type, it.name) },
            name = literal(name),
            code = literal(code),
        )
    }
}
