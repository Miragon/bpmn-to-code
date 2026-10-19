package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpCodeFormat.disambiguated
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpCodeFormat.nullableStringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpCodeFormat.pascalCase
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpCodeFormat.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpTemplateModel.FlowNode
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpTemplateModel.Member
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpTemplateModel.Variables
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.MappingFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.NodeFacets
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SequenceFlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedConstant
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SuccessorEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.VariableFacet

/**
 * Turns a node of the flow graph into the C# class the template renders: a sealed singleton reached as
 * `FlowNodes.<Node>.Instance`, with its facets, its successors behind `Next` and, for a subprocess, its interior's
 * start elements behind `Start`.
 *
 * Holder classes (`Successors`, `Interior`, `NodeVariables`, …) are named differently from the properties that expose
 * them, since C# rejects a member sharing its enclosing type's name (CS0102). Everything that crosses into another
 * node is expression-bodied, so static initialisation never cycles.
 */
internal object CSharpFlowNodeMapper {

    private const val FLOW_NODES = "FlowNodes"
    private const val NODE_VARIABLES = "NodeVariables"
    private const val SUCCESSORS = "Successors"
    private const val INTERIOR = "Interior"

    fun map(node: FlowGraphNode): FlowNode {
        val className = disambiguated(node.objectName, FLOW_NODES)
        return FlowNode(
            className = className,
            interfaces = interfacesOf(node, className).joinToString(", "),
            elementId = stringLiteral(node.id),
            elementType = CSharpRuntimeTypes.enumMember(CSharpRuntimeTypes.BPMN_ELEMENT_TYPE, node.elementType),
            eventType = node.eventType?.let { CSharpRuntimeTypes.enumMember(CSharpRuntimeTypes.BPMN_EVENT_TYPE, it) },
            name = nullableStringLiteral(node.name),
            properties = facetProperties(node.facets, className),
            variables = node.facets.variables.takeIf { it.isNotEmpty() }?.let { variablesOf(it) },
            inputs = mappingsOf(node.facets.inputs, holderName = "InputMappings"),
            outputs = mappingsOf(node.facets.outputs, holderName = "OutputMappings"),
            successors = node.successors.map { successorOf(it) },
            interiorStarts = node.interiorStarts.map { start ->
                expression(type = start.objectName, name = disambiguated(start.objectName, INTERIOR), value = "${start.objectName}.Instance")
            },
        )
    }

    /**
     * The holders in the generic arguments are qualified with the node, as the base list of a class does not see its
     * nested types.
     */
    private fun interfacesOf(node: FlowGraphNode, className: String): List<String> {
        val host = node.facets.attachedTo
        val kind = when {
            node.isBoundaryEvent && host != null -> "${CSharpRuntimeTypes.BOUNDARY_EVENT}<${host.objectName}>"
            node.eventType != null -> CSharpRuntimeTypes.EVENT
            else -> CSharpRuntimeTypes.FLOW_NODE
        }
        val successors = "${CSharpRuntimeTypes.HAS_SUCCESSORS}<$className.$SUCCESSORS>".takeIf { node.successors.isNotEmpty() }
        val scope = "${CSharpRuntimeTypes.FLOW_SCOPE}<$className.$INTERIOR>".takeIf { node.interiorStarts.isNotEmpty() }
        val facets = node.facets.facetInterfaces.map { CSharpRuntimeTypes.facetInterface(it.typeName) }
        return listOfNotNull(kind, successors, scope) + facets
    }

    /**
     * A facet member named like its node's class is renamed, because C# rejects a member named like its enclosing
     * type (CS0542).
     */
    private fun facetProperties(facets: NodeFacets, className: String): List<Member> {
        val jobType = facets.jobType?.let {
            readonly(type = "string", name = "JobType", value = CSharpSharedReferences.nodeValue(SharedDefinitionType.SERVICE_TASKS, it))
        }
        val calledProcess = facets.calledProcessId?.let {
            readonly(type = CSharpRuntimeTypes.PROCESS_ID, name = "CalledProcess", value = "new(${stringLiteral(it)})")
        }
        val timer = facets.timer?.let {
            val timerType = CSharpRuntimeTypes.enumMember(CSharpRuntimeTypes.TIMER_TYPE, it.type.name)
            readonly(type = CSharpRuntimeTypes.BPMN_TIMER, name = "Timer", value = "new($timerType, ${stringLiteral(it.expression)})")
        }
        val message = facets.message?.let {
            val value = CSharpSharedReferences.nodeValue(SharedDefinitionType.MESSAGES, it)
            readonly(type = CSharpRuntimeTypes.MESSAGE_NAME, name = "Message", value = value)
        }
        val signal = facets.signal?.let {
            val value = CSharpSharedReferences.nodeValue(SharedDefinitionType.SIGNALS, it)
            readonly(type = CSharpRuntimeTypes.SIGNAL_NAME, name = "Signal", value = value)
        }
        val error = facets.error?.let {
            val value = CSharpSharedReferences.nodeValueWithCode(SharedDefinitionType.ERRORS, it)
            readonly(type = CSharpRuntimeTypes.BPMN_ERROR_DEFINITION, name = "Error", value = value)
        }
        val escalation = facets.escalation?.let {
            val value = CSharpSharedReferences.nodeValueWithCode(SharedDefinitionType.ESCALATIONS, it)
            readonly(type = CSharpRuntimeTypes.BPMN_ESCALATION_DEFINITION, name = "Escalation", value = value)
        }
        val attachedTo = facets.attachedTo?.let {
            expression(type = it.objectName, name = "AttachedTo", value = "${it.objectName}.Instance")
        }
        val isInterrupting = facets.isInterrupting?.let {
            expression(type = "bool", name = "IsInterrupting", value = it.toString())
        }
        val properties = listOfNotNull(jobType, calledProcess, timer, message, signal, error, escalation, attachedTo, isInterrupting)
        return properties.map { it.copy(name = disambiguated(it.name, className)) }
    }

    private fun variablesOf(variables: List<VariableFacet>): Variables {
        val members = variables.map { variable ->
            val nameConstant = CSharpSharedReferences.reference(SharedDefinitionType.PROCESS_VARIABLES, SharedConstant(variable.constantName))
            readonly(
                type = "${CSharpRuntimeTypes.VARIABLE_NAME}.${variable.subtype.simpleName}",
                name = disambiguated(variablePropertyName(variable), NODE_VARIABLES),
                value = "new($nameConstant)",
            )
        }
        return Variables(members = members, all = variables.joinToString(", ") { variablePropertyName(it) })
    }

    // A variable called `all`, `inputs` or `outputs` would hide the member of that name it inherits from `VariableDefinitions`.
    private fun variablePropertyName(variable: VariableFacet): String {
        val propertyName = pascalCase(variable.rawName)
        return if (propertyName in CSharpRuntimeTypes.VARIABLE_DEFINITIONS_MEMBERS) propertyName + "_" else propertyName
    }

    private fun mappingsOf(mappings: List<MappingFacet>, holderName: String): List<Member> = mappings.map { mapping ->
        val value = "new(${stringLiteral(mapping.target)}, ${nullableStringLiteral(mapping.source)}, ${nullableStringLiteral(mapping.sourceExpression)})"
        readonly(type = CSharpRuntimeTypes.INPUT_OUTPUT_MAPPING, name = disambiguated(pascalCase(mapping.target), holderName), value = value)
    }

    /**
     * A successor reached by sequence flows is the `SequenceFlows` carrying them; one reached without a flow is an
     * `AttachedBoundaryEvent` or an `AssociatedCompensationHandler`. The target is qualified with `FlowNodes`, as the
     * successor property named after it shadows its type.
     */
    private fun successorOf(successor: SuccessorEdge): Member {
        val targetName = successor.target.objectName
        val target = "$FLOW_NODES.$targetName.Instance"
        val (successorType, value) = when (successor) {
            is SuccessorEdge.ViaSequenceFlows -> CSharpRuntimeTypes.SEQUENCE_FLOWS to sequenceFlowsOf(successor, target)
            is SuccessorEdge.AttachedBoundaryEvent -> CSharpRuntimeTypes.ATTACHED_BOUNDARY_EVENT to "new($target)"
            is SuccessorEdge.AssociatedCompensationHandler -> CSharpRuntimeTypes.ASSOCIATED_COMPENSATION_HANDLER to "new($target)"
        }
        return expression(type = "$successorType<$targetName>", name = disambiguated(targetName, SUCCESSORS), value = value)
    }

    private fun sequenceFlowsOf(successor: SuccessorEdge.ViaSequenceFlows, target: String): String {
        val flows = successor.flows
        val singleFlow = flows.singleOrNull()
        if (singleFlow != null) {
            return "${CSharpRuntimeTypes.SEQUENCE_FLOWS}.Single(${singleFlowArguments(singleFlow, target)})"
        }
        val flowType = "${CSharpRuntimeTypes.SEQUENCE_FLOW}<${successor.target.objectName}>"
        val constructions = flows.map { flow ->
            "new(new(${stringLiteral(flow.id)}), ${nullableStringLiteral(flow.name)}, ${nullableStringLiteral(flow.conditionExpression)}, ${flow.isDefault}, $target)"
        }
        return "new($target, new $flowType[] { ${constructions.joinToString(", ")} })"
    }

    private fun singleFlowArguments(flow: SequenceFlowEdge, target: String): String {
        val metadata = listOfNotNull(
            flow.name?.let { "name: ${stringLiteral(it)}" },
            flow.conditionExpression?.let { "conditionExpression: ${stringLiteral(it)}" },
            "isDefault: true".takeIf { flow.isDefault },
        )
        return (listOf("new(${stringLiteral(flow.id)})", target) + metadata).joinToString(", ")
    }

    private fun readonly(type: String, name: String, value: String) = Member(type = type, name = name, value = value, expressionBodied = false)

    private fun expression(type: String, name: String, value: String) = Member(type = type, name = name, value = value, expressionBodied = true)
}
