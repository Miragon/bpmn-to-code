package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptSyntax.identifier
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptSyntax.nullableStringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptSyntax.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptTemplateModel.Entry
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptTemplateModel.Field
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptTemplateModel.FlowNode
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptTemplateModel.Getter
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.MappingFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.NodeFacets
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SequenceFlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedConstant
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SuccessorEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.VariableFacet

/**
 * Turns a node of the flow graph into the TypeScript class the template renders: a singleton behind `INSTANCE` with
 * its metadata and facets as readonly fields, its successors behind `next` and, for a subprocess, its interior's start
 * elements behind `startEvents`. Whatever points at another node is a getter, so no class touches another one while
 * the module loads.
 */
internal class TypeScriptFlowNodeMapper(private val shared: TypeScriptSharedReferences) {

    fun map(node: FlowGraphNode): FlowNode = FlowNode(
        className = identifier(node.objectName),
        interfaces = interfacesOf(node).joinToString(", "),
        elementId = stringLiteral(node.id),
        elementType = stringLiteral(node.elementType),
        eventType = node.eventType?.let { stringLiteral(it) },
        name = nullableStringLiteral(node.name),
        fields = facetFields(node.facets),
        attachedTo = node.facets.attachedTo?.let { identifier(it.objectName) },
        variables = node.facets.variables.map { variableOf(it) },
        inputs = node.facets.inputs.map { mappingOf(it) },
        outputs = node.facets.outputs.map { mappingOf(it) },
        successors = node.successors.map { successorOf(it) },
        interiorStarts = node.interiorStarts.map { start ->
            Getter(name = identifier(start.propertyName), type = start.objectName, value = "${start.objectName}.INSTANCE")
        },
    )

    private fun interfacesOf(node: FlowGraphNode): List<String> {
        val host = node.facets.attachedTo
        val kind = when {
            node.isBoundaryEvent && host != null -> "Runtime.BoundaryEvent<${host.objectName}>"
            node.eventType != null -> "Runtime.Event"
            else -> "Runtime.FlowNode"
        }
        val successors = "Runtime.HasSuccessors".takeIf { node.successors.isNotEmpty() }
        val scope = "Runtime.FlowScope".takeIf { node.interiorStarts.isNotEmpty() }
        val facets = node.facets.facetInterfaces.map { "Runtime.${it.typeName}" }
        return listOfNotNull(kind, successors, scope) + facets
    }

    private fun facetFields(facets: NodeFacets): List<Field> {
        val jobType = facets.jobType?.let { Field(name = "jobType", type = null, value = shared.nodeValue(SharedDefinitionType.SERVICE_TASKS, it)) }
        val calledProcess = facets.calledProcessId?.let { Field(name = "calledProcess", type = null, value = stringLiteral(it)) }
        val timer = facets.timer?.let {
            val value = "{ type: ${stringLiteral(it.type.name)}, timerValue: ${stringLiteral(it.expression)} }"
            Field(name = "timer", type = "Runtime.BpmnTimer", value = value)
        }
        val message = facets.message?.let { Field(name = "message", type = null, value = shared.nodeValue(SharedDefinitionType.MESSAGES, it)) }
        val signal = facets.signal?.let { Field(name = "signal", type = null, value = shared.nodeValue(SharedDefinitionType.SIGNALS, it)) }
        val error = facets.error?.let {
            Field(name = "error", type = "Runtime.BpmnErrorDefinition", value = shared.nodeValueWithCode(SharedDefinitionType.ERRORS, it))
        }
        val escalation = facets.escalation?.let {
            val value = shared.nodeValueWithCode(SharedDefinitionType.ESCALATIONS, it)
            Field(name = "escalation", type = "Runtime.BpmnEscalationDefinition", value = value)
        }
        val isInterrupting = facets.isInterrupting?.let { Field(name = "isInterrupting", type = null, value = it.toString()) }
        return listOfNotNull(jobType, calledProcess, timer, message, signal, error, escalation, isInterrupting)
    }

    private fun variableOf(variable: VariableFacet): Entry {
        val register = variable.subtype.simpleName.replaceFirstChar { it.lowercaseChar() }
        val nameConstant = shared.reference(SharedDefinitionType.PROCESS_VARIABLES, SharedConstant(variable.constantName))
        return Entry(name = identifier(variable.constantName), value = "Runtime.$register($nameConstant)")
    }

    private fun mappingOf(mapping: MappingFacet): Entry {
        val source = nullableStringLiteral(mapping.source)
        val sourceExpression = nullableStringLiteral(mapping.sourceExpression)
        val value = "{ target: ${stringLiteral(mapping.target)}, source: $source, sourceExpression: $sourceExpression }"
        return Entry(name = identifier(mapping.constantName), value = value)
    }

    /**
     * A successor reached by sequence flows is the `SequenceFlows` carrying them; one reached without a flow is an
     * `AttachedBoundaryEvent` or an `AssociatedCompensationHandler`.
     */
    private fun successorOf(successor: SuccessorEdge): Getter {
        val targetName = successor.target.objectName
        val target = "$targetName.INSTANCE"
        val (successorType, value) = when (successor) {
            is SuccessorEdge.ViaSequenceFlows -> {
                val flows = successor.flows.joinToString(", ") { flowOf(it) }
                "Runtime.SequenceFlows" to "Runtime.sequenceFlows($target, [$flows])"
            }

            is SuccessorEdge.AttachedBoundaryEvent -> "Runtime.AttachedBoundaryEvent" to "Runtime.attachedBoundaryEvent($target)"

            is SuccessorEdge.AssociatedCompensationHandler -> {
                "Runtime.AssociatedCompensationHandler" to "Runtime.associatedCompensationHandler($target)"
            }
        }
        return Getter(name = identifier(successor.target.propertyName), type = "$successorType<$targetName>", value = value)
    }

    private fun flowOf(flow: SequenceFlowEdge): String {
        val name = nullableStringLiteral(flow.name)
        val condition = nullableStringLiteral(flow.conditionExpression)
        return "{ id: ${stringLiteral(flow.id)}, name: $name, conditionExpression: $condition, isDefault: ${flow.isDefault} }"
    }
}
