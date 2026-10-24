package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.PROCESS_VARIABLES
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpSyntax.pascalCase
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.MappingFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedValue
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.VariableFacet
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.FlowNodeModel.NodeName
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.FlowNodeSyntax
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.SharedNameAndCode
import io.miragon.bpmn.domain.ProcessApiNamingException

/**
 * How a C# flow node is written: a sealed singleton class reached through a property of `FlowNodes`, e.g.
 * (`Runtime` standing for `global::Miragon.BpmnToCode.Runtime`)
 * ```
 * public static ServiceTaskSendContractNode ServiceTaskSendContract => ServiceTaskSendContractNode.Instance;
 *
 * public sealed class ServiceTaskSendContractNode : Runtime.IFlowNode,
 *     Runtime.IHasSuccessors<ServiceTaskSendContractNode.Successors>, Runtime.IHasJobType, Runtime.IHasVariables
 * ```
 * `Next` and `Start` carry the runtime's navigation interfaces, which a `ProcessPath` walks along.
 */
internal class CSharpFlowNodeSyntax(private val names: CSharpNodeNames) : FlowNodeSyntax {

    override fun literal(value: String): String = CSharpSyntax.stringLiteral(value)

    override fun enumMember(name: String): String = pascalCase(name)

    override fun nodeName(node: FlowEdge): NodeName = names.of(node.objectName)

    override fun interfacesOf(node: FlowGraphNode): List<String> {
        val typeName = names.of(node.objectName).type
        val facetInterfaces = node.facets.facetInterfaces.map { CSharpRuntimeTypes.facetInterface(it.typeName) }
        return listOf(nodeInterfaceOf(node)) + navigationInterfacesOf(node, typeName) + facetInterfaces
    }

    override fun sharedReference(type: SharedDefinitionType, constantName: String): String = CSharpSharedReferences.reference(type, constantName)

    /**
     * A variable called `all`, `inputs` or `outputs` would hide the member of that name it inherits from
     * `VariableDefinitions`, one called `processVariables` the class its name constant lives in, and one called
     * `nodeVariables` would be named like its holder. Nothing is renamed, so generation fails.
     */
    override fun variableName(variable: VariableFacet): String {
        val propertyName = pascalCase(variable.rawName)
        val reservedNames = CSharpRuntimeTypes.VARIABLE_DEFINITIONS_MEMBERS + PROCESS_VARIABLES.typeName + VARIABLES_HOLDER
        return requireUnreserved(name = propertyName, reservedNames = reservedNames, generatedFrom = "variable '${variable.rawName}'")
    }

    /**
     * A mapping called `inputMappings` or `outputMappings` would be named like its holder.
     */
    override fun mappingName(mapping: MappingFacet): String = requireUnreserved(
        name = pascalCase(mapping.target),
        reservedNames = MAPPING_HOLDERS,
        generatedFrom = "mapping target '${mapping.target}'",
    )

    override fun sharedNameAndCode(type: SharedDefinitionType, sharedValue: SharedValue<Pair<String, String>>): SharedNameAndCode = CSharpSharedReferences.nameAndCode(type, sharedValue)

    private fun requireUnreserved(name: String, reservedNames: Set<String>, generatedFrom: String): String {
        if (name !in reservedNames) return name
        throw ProcessApiNamingException("C# reserves the name '$name' of the $generatedFrom inside a node. Rename it.")
    }

    private fun nodeInterfaceOf(node: FlowGraphNode): String = when {
        node.isBoundaryEvent -> CSharpRuntimeTypes.BOUNDARY_EVENT
        node.eventType != null -> CSharpRuntimeTypes.EVENT
        else -> CSharpRuntimeTypes.FLOW_NODE
    }

    /**
     * `IHasSuccessors<…Node.Successors>` for `Next`, `IFlowScope<…Node.Interior>` for `Start`, and
     * `ISubProcess<…Node.Interior, …Node.Successors>` for a subprocess that has both.
     */
    private fun navigationInterfacesOf(node: FlowGraphNode, typeName: String): List<String> {
        val successors = "$typeName.Successors"
        val interior = "$typeName.Interior"
        val hasSuccessors = node.successors.isNotEmpty()
        val hasInterior = node.interiorStarts.isNotEmpty()
        return when {
            hasSuccessors && hasInterior -> listOf("${CSharpRuntimeTypes.SUB_PROCESS}<$interior, $successors>")
            hasSuccessors -> listOf("${CSharpRuntimeTypes.HAS_SUCCESSORS}<$successors>")
            hasInterior -> listOf("${CSharpRuntimeTypes.FLOW_SCOPE}<$interior>")
            else -> emptyList()
        }
    }

    private companion object {
        const val VARIABLES_HOLDER = "NodeVariables"
        val MAPPING_HOLDERS = setOf("InputMappings", "OutputMappings")
    }
}
