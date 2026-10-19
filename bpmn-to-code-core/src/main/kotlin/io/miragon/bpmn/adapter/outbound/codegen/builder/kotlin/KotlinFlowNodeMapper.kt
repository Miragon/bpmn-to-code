package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinSyntax.identifier
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinSyntax.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinTemplateModel.Argument
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinTemplateModel.Call
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinTemplateModel.FlowNode
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinTemplateModel.Getter
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinTemplateModel.Property
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.MappingFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.NodeFacets
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SequenceFlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedConstant
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SuccessorEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.VariableFacet
import io.miragon.bpmn.adapter.outbound.codegen.template.TypeReferences

/**
 * Turns a node of the flow graph into the Kotlin object the template renders: it carries its metadata via
 * `AbstractFlowNode`, its facets as properties implementing the runtime facet interfaces, its successors behind `next`
 * and, for a subprocess, its interior's start elements behind `startEvents`. `Variables` registers each variable it
 * declares, so its `all` needs no generated list. Whatever points at another node is a getter, so object
 * initialisation never touches another node.
 */
internal class KotlinFlowNodeMapper(private val t: TypeReferences) {

    private val shared = KotlinSharedReferences(t)

    fun map(node: FlowGraphNode): FlowNode {
        val objectName = identifier(node.objectName)
        return FlowNode(
            objectName = objectName,
            superArguments = listOfNotNull(
                Argument(name = "id", value = "${t.of("ElementId")}($objectName.ELEMENT_ID)"),
                Argument(name = "elementType", value = "${t.of("BpmnElementType")}.${node.elementType}"),
                node.name?.let { Argument(name = "name", value = stringLiteral(it)) },
            ),
            supertypes = supertypesOf(node).joinToString(", "),
            properties = propertiesOf(node),
            variables = node.facets.variables.map { variableOf(it) },
            inputs = node.facets.inputs.map { mappingOf(it) },
            outputs = node.facets.outputs.map { mappingOf(it) },
            successors = node.successors.map { successorOf(it) },
            interiorStarts = node.interiorStarts.map { start ->
                val startName = identifier(start.objectName)
                Property(modifiers = "", name = identifier(start.propertyName), type = startName, getter = Getter(value = startName))
            },
        )
    }

    // A bare `Next` in the supertype header would bind to an enclosing object's `Next`; it is qualified with the node.
    private fun supertypesOf(node: FlowGraphNode): List<String> {
        val objectName = identifier(node.objectName)
        val successors = if (node.successors.isEmpty()) null else "${t.of("HasSuccessors")}<$objectName.Next>"
        val host = node.facets.attachedTo
        val event = when {
            node.isBoundaryEvent && host != null -> "${t.of("BoundaryEvent")}<${identifier(host.objectName)}>"
            node.eventType != null -> t.of("Event")
            else -> null
        }
        val scope = if (node.interiorStarts.isEmpty()) null else "${t.of("FlowScope")}<$objectName.Start>"
        val facets = node.facets.facetInterfaces.map { t.of(it.typeName) }
        return listOfNotNull(successors, event, scope) + facets
    }

    private fun propertiesOf(node: FlowGraphNode): List<Property> {
        val eventType = node.eventType?.let {
            Property(modifiers = OVERRIDE, name = "eventType", type = t.of("BpmnEventType"), value = "${t.of("BpmnEventType")}.$it")
        }
        val elementId = Property(modifiers = "const ", name = "ELEMENT_ID", type = t.of("String"), value = stringLiteral(node.id))
        val next = Property(modifiers = OVERRIDE, name = "next", type = "Next", value = "Next").takeIf { node.successors.isNotEmpty() }
        val startEvents = Property(modifiers = OVERRIDE, name = "startEvents", type = "Start", value = "Start").takeIf { node.interiorStarts.isNotEmpty() }
        return listOfNotNull(eventType, elementId) + facetProperties(node.facets) + listOfNotNull(next, startEvents)
    }

    private fun facetProperties(facets: NodeFacets): List<Property> {
        val jobType = facets.jobType?.let { shared.nodeProperty(type = SharedDefinitionType.SERVICE_TASKS, name = "jobType", shared = it) }
        val calledProcess = facets.calledProcessId?.let {
            Property(modifiers = OVERRIDE, name = "calledProcess", type = t.of("ProcessId"), value = "${t.of("ProcessId")}(${stringLiteral(it)})")
        }
        val timer = facets.timer?.let {
            val arguments = listOf(
                Argument(name = "type", value = "${t.of("TimerType")}.${it.type.name}"),
                Argument(name = "timerValue", value = stringLiteral(it.expression)),
            )
            Property(modifiers = OVERRIDE, name = "timer", type = t.of("BpmnTimer"), call = Call(function = t.of("BpmnTimer"), arguments = arguments))
        }
        val message = facets.message?.let { shared.nodeProperty(type = SharedDefinitionType.MESSAGES, name = "message", shared = it) }
        val signal = facets.signal?.let { shared.nodeProperty(type = SharedDefinitionType.SIGNALS, name = "signal", shared = it) }
        val error = facets.error?.let { shared.nodePropertyWithCode(type = SharedDefinitionType.ERRORS, name = "error", shared = it) }
        val escalation = facets.escalation?.let { shared.nodePropertyWithCode(type = SharedDefinitionType.ESCALATIONS, name = "escalation", shared = it) }
        val attachedTo = facets.attachedTo?.let {
            val hostName = identifier(it.objectName)
            Property(modifiers = OVERRIDE, name = "attachedTo", type = hostName, getter = Getter(value = hostName))
        }
        val isInterrupting = facets.isInterrupting?.let {
            val modifiers = if (facets.attachedTo != null) OVERRIDE else ""
            Property(modifiers = modifiers, name = "isInterrupting", type = t.of("Boolean"), value = it.toString())
        }
        val variables = Property(modifiers = OVERRIDE, name = "variables", type = "Variables", value = "Variables").takeIf { facets.variables.isNotEmpty() }
        return listOfNotNull(jobType, calledProcess, timer, message, signal, error, escalation, attachedTo, isInterrupting, variables)
    }

    private fun variableOf(variable: VariableFacet): Property {
        val subtype = "${t.of("VariableName")}.${variable.subtype.simpleName}"
        val register = variable.subtype.simpleName.replaceFirstChar { it.lowercaseChar() }
        val nameConstant = shared.reference(SharedDefinitionType.PROCESS_VARIABLES, SharedConstant(variable.constantName))
        return Property(modifiers = "", name = identifier(variable.constantName), type = subtype, value = "$register($nameConstant)")
    }

    private fun mappingOf(mapping: MappingFacet): Property {
        val arguments = listOfNotNull(
            Argument(name = "target", value = stringLiteral(mapping.target)),
            mapping.source?.let { Argument(name = "source", value = stringLiteral(it)) },
            mapping.sourceExpression?.let { Argument(name = "sourceExpression", value = stringLiteral(it)) },
        )
        val mappingType = t.of("InputOutputMapping")
        return Property(modifiers = "", name = identifier(mapping.constantName), type = mappingType, call = Call(function = mappingType, arguments = arguments))
    }

    /**
     * A successor reached by sequence flows is the `SequenceFlows` carrying them; one reached without a flow is an
     * `AttachedBoundaryEvent` or an `AssociatedCompensationHandler`. A single flow is created via `SequenceFlows.single`,
     * several flows to the same target are listed.
     */
    private fun successorOf(successor: SuccessorEdge): Property {
        val targetName = identifier(successor.target.objectName)
        val (successorType, getter) = when (successor) {
            is SuccessorEdge.ViaSequenceFlows -> t.of("SequenceFlows") to Getter(call = sequenceFlowsOf(successor, targetName))

            is SuccessorEdge.AttachedBoundaryEvent -> {
                t.of("AttachedBoundaryEvent") to Getter(value = "${t.of("AttachedBoundaryEvent")}(target = $targetName)")
            }

            is SuccessorEdge.AssociatedCompensationHandler -> {
                t.of("AssociatedCompensationHandler") to Getter(value = "${t.of("AssociatedCompensationHandler")}(target = $targetName)")
            }
        }
        return Property(modifiers = "", name = identifier(successor.target.propertyName), type = "$successorType<$targetName>", getter = getter)
    }

    private fun sequenceFlowsOf(successor: SuccessorEdge.ViaSequenceFlows, targetName: String): Call {
        val singleFlow = successor.flows.singleOrNull()
        if (singleFlow != null) {
            val arguments = listOf(Argument(name = "flowId", value = elementIdOf(singleFlow))) + flowMetadata(singleFlow, targetName)
            return Call(function = "${t.of("SequenceFlows")}.single", arguments = arguments)
        }
        val flows = successor.flows.map { flow ->
            val arguments = listOf(Argument(name = "id", value = elementIdOf(flow))) + flowMetadata(flow, targetName)
            Call(function = t.of("SequenceFlow"), arguments = arguments)
        }
        val arguments = listOf(Argument(name = "target", value = targetName), Argument(name = "flows", calls = flows))
        return Call(function = t.of("SequenceFlows"), arguments = arguments)
    }

    private fun elementIdOf(flow: SequenceFlowEdge) = "${t.of("ElementId")}(${stringLiteral(flow.id)})"

    private fun flowMetadata(flow: SequenceFlowEdge, targetName: String): List<Argument> = listOfNotNull(
        flow.name?.let { Argument(name = "name", value = stringLiteral(it)) },
        flow.conditionExpression?.let { Argument(name = "conditionExpression", value = stringLiteral(it)) },
        Argument(name = "isDefault", value = "true").takeIf { flow.isDefault },
        Argument(name = "target", value = targetName),
    )

    private companion object {
        const val OVERRIDE = "override "
    }
}
