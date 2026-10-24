package io.miragon.bpmn.adapter.outbound.codegen.flownode

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.MappingFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.NodeFacets
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SequenceFlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedConstant
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedValue
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SuccessorEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.VariableFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.VariableNameSubtype
import io.miragon.bpmn.adapter.outbound.codegen.flownode.FlowNodeModel.Flow
import io.miragon.bpmn.adapter.outbound.codegen.flownode.FlowNodeModel.Mapping
import io.miragon.bpmn.adapter.outbound.codegen.flownode.FlowNodeModel.NodeName
import io.miragon.bpmn.adapter.outbound.codegen.flownode.FlowNodeModel.Successor
import io.miragon.bpmn.adapter.outbound.codegen.flownode.FlowNodeModel.Variable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FlowNodeMapperTest {

    private val underTest = FlowNodeMapper(MarkingSyntax)

    @Test
    fun `refers to the shared constant holding a value, and writes the value when none does`() {
        // given: a task whose job type has a shared constant and whose message has none
        val node = sendContractTask(
            facets = NodeFacets(
                jobType = SharedValue(value = "miravelo.sendContract", constant = SharedConstant(name = "MIRAVELO_SEND_CONTRACT")),
                message = SharedValue(value = "miravelo.contractSent", constant = null),
            ),
        )

        // when: mapping the node
        val actual = underTest.map(node)

        // then: the job type is a reference and the message a literal
        assertThat(actual.jobType).isEqualTo("ServiceTasks.MIRAVELO_SEND_CONTRACT")
        assertThat(actual.message).isEqualTo(SharedName(reference = null, literal = "'miravelo.contractSent'"))
    }

    @Test
    fun `writes names, literals and enum members through the syntax of the language`() {
        // given: a task with a variable and an input mapping
        val node = sendContractTask(
            facets = NodeFacets(
                variables = listOf(VariableFacet(constantName = "CONTRACT_ID", rawName = "contractId", subtype = VariableNameSubtype.INPUT)),
                inputs = listOf(MappingFacet(constantName = "CONTRACT_ID", target = "contractId", source = null, sourceExpression = "=contractId")),
            ),
        )

        // when: mapping the node
        val actual = underTest.map(node)

        // then: every value is written by the syntax, a missing optional value stays null
        assertThat(actual.type).isEqualTo("ServiceTaskSendContract")
        assertThat(actual.property).isEqualTo("serviceTaskSendContract")
        assertThat(actual.elementId).isEqualTo("'serviceTask_sendContract'")
        assertThat(actual.elementType).isEqualTo("enum:SERVICE_TASK")
        assertThat(actual.name).isEqualTo("'Send contract'")
        assertThat(actual.variables).containsExactly(
            Variable(name = "var:CONTRACT_ID", subtype = "Input", nameConstant = "ProcessVariables.CONTRACT_ID"),
        )
        assertThat(actual.inputs).containsExactly(
            Mapping(name = "mapping:CONTRACT_ID", target = "'contractId'", source = null, sourceExpression = "'=contractId'"),
        )
    }

    @Test
    fun `names the runtime type leading to each successor`() {
        // given: a task followed by a flow and carrying a boundary event
        val node = sendContractTask(
            successors = listOf(
                SuccessorEdge.ViaSequenceFlows(
                    target = END_EVENT,
                    flows = listOf(SequenceFlowEdge(id = "flow_contractSent", name = null, conditionExpression = null, isDefault = false)),
                ),
                SuccessorEdge.AttachedBoundaryEvent(target = BOUNDARY_EVENT),
            ),
        )

        // when: mapping the node
        val actual = underTest.map(node)

        // then: the flow leads to the end event, the boundary event is reached without one
        assertThat(actual.successors).containsExactly(
            Successor(
                target = NodeName(type = "EndEventContractSent", property = "endEventContractSent"),
                type = "SequenceFlows",
                flows = listOf(Flow(id = "'flow_contractSent'", name = null, conditionExpression = null, defaultFlow = false)),
            ),
            Successor(
                target = NodeName(type = "TimerSignatureDeadline", property = "timerSignatureDeadline"),
                type = "AttachedBoundaryEvent",
                flows = emptyList(),
            ),
        )
    }

    private fun sendContractTask(
        facets: NodeFacets = NodeFacets(),
        successors: List<SuccessorEdge> = emptyList(),
    ) = FlowGraphNode(
        objectName = "ServiceTaskSendContract",
        propertyName = "serviceTaskSendContract",
        id = "serviceTask_sendContract",
        elementType = "SERVICE_TASK",
        eventType = null,
        name = "Send contract",
        isBoundaryEvent = false,
        successors = successors,
        interiorStarts = emptyList(),
        facets = facets,
    )

    private object MarkingSyntax : FlowNodeSyntax {

        override fun literal(value: String): String = "'$value'"

        override fun enumMember(name: String): String = "enum:$name"

        override fun nodeName(node: FlowEdge): NodeName = NodeName(type = node.objectName, property = node.propertyName)

        override fun interfacesOf(node: FlowGraphNode): List<String> = emptyList()

        override fun sharedReference(type: SharedDefinitionType, constantName: String): String = "${type.typeName}.$constantName"

        override fun variableName(variable: VariableFacet): String = "var:${variable.constantName}"

        override fun mappingName(mapping: MappingFacet): String = "mapping:${mapping.constantName}"
    }

    private companion object {
        val END_EVENT = FlowEdge(propertyName = "endEventContractSent", objectName = "EndEventContractSent")
        val BOUNDARY_EVENT = FlowEdge(propertyName = "timerSignatureDeadline", objectName = "TimerSignatureDeadline")
    }
}
