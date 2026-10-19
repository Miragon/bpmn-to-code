package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaSyntax.identifier
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaSyntax.nullableStringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaSyntax.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaTemplateModel.Accessor
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaTemplateModel.Constant
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaTemplateModel.FlowNode
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaTemplateModel.Getter
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaTemplateModel.Variables
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.MappingFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.NodeFacets
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SequenceFlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedConstant
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SuccessorEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.VariableFacet
import io.miragon.bpmn.adapter.outbound.codegen.template.TypeReferences

/**
 * Turns a node of the flow graph into the Java class the template renders: a singleton behind `INSTANCE` that carries
 * its metadata via `AbstractFlowNode`, its facets as getters implementing the runtime facet interfaces, its successors
 * behind `getNext()` and, for a subprocess, its interior's start elements behind `getStartEvents()`.
 */
internal class JavaFlowNodeMapper(private val t: TypeReferences) {

    private val shared = JavaSharedReferences(t)

    fun map(node: FlowGraphNode): FlowNode = FlowNode(
        className = node.objectName,
        accessorName = identifier(node.propertyName),
        interfaces = interfacesOf(node).joinToString(", "),
        elementId = stringLiteral(node.id),
        elementType = "${t.of("BpmnElementType")}.${node.elementType}",
        name = node.name?.let { stringLiteral(it) },
        getters = gettersOf(node),
        variables = node.facets.variables.takeIf { it.isNotEmpty() }?.let { variablesOf(it) },
        inputs = mappingsOf(node.facets.inputs),
        outputs = mappingsOf(node.facets.outputs),
        successors = node.successors.map { successorOf(it) },
        interiorStarts = node.interiorStarts.map { start ->
            Accessor(type = start.objectName, name = identifier(start.propertyName), value = "${start.objectName}.INSTANCE")
        },
    )

    // A bare `Next` in the implements clause would bind to an enclosing class's `Next`; it is qualified with the node.
    private fun interfacesOf(node: FlowGraphNode): List<String> {
        val successors = if (node.successors.isEmpty()) null else "${t.of("HasSuccessors")}<${node.objectName}.Next>"
        val host = node.facets.attachedTo
        val event = when {
            node.isBoundaryEvent && host != null -> "${t.of("BoundaryEvent")}<${host.objectName}>"
            node.eventType != null -> t.of("Event")
            else -> null
        }
        val scope = if (node.interiorStarts.isEmpty()) null else "${t.of("FlowScope")}<${node.objectName}.Start>"
        val facets = node.facets.facetInterfaces.map { t.of(it.typeName) }
        return listOfNotNull(successors, event, scope) + facets
    }

    private fun gettersOf(node: FlowGraphNode): List<Getter> {
        val eventType = node.eventType?.let {
            getter(type = t.of("BpmnEventType"), name = "getEventType", value = "${t.of("BpmnEventType")}.$it")
        }
        return listOfNotNull(eventType) + facetGetters(node.facets)
    }

    private fun facetGetters(facets: NodeFacets): List<Getter> {
        val jobType = facets.jobType?.let {
            getter(type = t.of("String"), name = "getJobType", value = shared.nodeValue(SharedDefinitionType.SERVICE_TASKS, it))
        }
        val calledProcess = facets.calledProcessId?.let {
            getter(type = t.of("ProcessId"), name = "getCalledProcess", value = "new ${t.of("ProcessId")}(${stringLiteral(it)})")
        }
        val timer = facets.timer?.let {
            val value = "new ${t.of("BpmnTimer")}(${t.of("TimerType")}.${it.type.name}, ${stringLiteral(it.expression)})"
            getter(type = t.of("BpmnTimer"), name = "getTimer", value = value)
        }
        val message = facets.message?.let {
            getter(type = t.of("MessageName"), name = "getMessage", value = shared.nodeValue(SharedDefinitionType.MESSAGES, it))
        }
        val signal = facets.signal?.let {
            getter(type = t.of("SignalName"), name = "getSignal", value = shared.nodeValue(SharedDefinitionType.SIGNALS, it))
        }
        val error = facets.error?.let {
            getter(type = t.of("BpmnErrorDefinition"), name = "getError", value = shared.nodeValueWithCode(SharedDefinitionType.ERRORS, it))
        }
        val escalation = facets.escalation?.let {
            val value = shared.nodeValueWithCode(SharedDefinitionType.ESCALATIONS, it)
            getter(type = t.of("BpmnEscalationDefinition"), name = "getEscalation", value = value)
        }
        val attachedTo = facets.attachedTo?.let {
            getter(type = it.objectName, name = "getAttachedTo", value = "${it.objectName}.INSTANCE")
        }
        val isInterrupting = facets.isInterrupting?.let {
            Getter(type = "boolean", name = "isInterrupting", value = it.toString(), overrides = facets.attachedTo != null)
        }
        val variables = facets.variables.takeIf { it.isNotEmpty() }?.let {
            getter(type = "Variables", name = "getVariables", value = "VARIABLES")
        }
        return listOfNotNull(jobType, calledProcess, timer, message, signal, error, escalation, attachedTo, isInterrupting, variables)
    }

    private fun variablesOf(variables: List<VariableFacet>): Variables {
        val constants = variables.map { variable ->
            val subtype = "${t.of("VariableName")}.${variable.subtype.simpleName}"
            val nameConstant = shared.reference(SharedDefinitionType.PROCESS_VARIABLES, SharedConstant(variable.constantName))
            Constant(type = subtype, name = identifier(variable.constantName), value = "new $subtype($nameConstant)")
        }
        return Variables(constants = constants, all = constants.joinToString(", ") { it.name })
    }

    private fun mappingsOf(mappings: List<MappingFacet>): List<Constant> = mappings.map { mapping ->
        val arguments = listOf(mapping.target, mapping.source, mapping.sourceExpression).joinToString(", ") { nullableStringLiteral(it) }
        val mappingType = t.of("InputOutputMapping")
        Constant(type = mappingType, name = identifier(mapping.constantName), value = "new $mappingType($arguments)")
    }

    /**
     * A successor reached by sequence flows is the `SequenceFlows` carrying them; one reached without a flow is an
     * `AttachedBoundaryEvent` or an `AssociatedCompensationHandler`. A single flow is created via `SequenceFlows.single`,
     * several flows to the same target are listed.
     */
    private fun successorOf(successor: SuccessorEdge): Accessor {
        val targetName = successor.target.objectName
        val target = "$targetName.INSTANCE"
        val (successorType, value) = when (successor) {
            is SuccessorEdge.ViaSequenceFlows -> t.of("SequenceFlows") to sequenceFlowsOf(successor, target)

            is SuccessorEdge.AttachedBoundaryEvent -> t.of("AttachedBoundaryEvent") to "new ${t.of("AttachedBoundaryEvent")}<>($target)"

            is SuccessorEdge.AssociatedCompensationHandler -> {
                t.of("AssociatedCompensationHandler") to "new ${t.of("AssociatedCompensationHandler")}<>($target)"
            }
        }
        return Accessor(type = "$successorType<$targetName>", name = identifier(successor.target.propertyName), value = value)
    }

    private fun sequenceFlowsOf(successor: SuccessorEdge.ViaSequenceFlows, target: String): String {
        val singleFlow = successor.flows.singleOrNull()
        if (singleFlow != null) {
            return "${t.of("SequenceFlows")}.single(${flowArguments(singleFlow, target)})"
        }
        val flows = successor.flows.joinToString(", ") { flow -> "new ${t.of("SequenceFlow")}<>(${flowArguments(flow, target)})" }
        return "new ${t.of("SequenceFlows")}<>($target, ${t.of("List")}.of($flows))"
    }

    private fun flowArguments(flow: SequenceFlowEdge, target: String): String {
        val id = "new ${t.of("ElementId")}(${stringLiteral(flow.id)})"
        if (flow.hasOnlyDefaults()) return "$id, $target"
        return "$id, ${nullableStringLiteral(flow.name)}, ${nullableStringLiteral(flow.conditionExpression)}, ${flow.isDefault}, $target"
    }

    private fun getter(type: String, name: String, value: String) = Getter(type = type, name = name, value = value, overrides = true)
}
