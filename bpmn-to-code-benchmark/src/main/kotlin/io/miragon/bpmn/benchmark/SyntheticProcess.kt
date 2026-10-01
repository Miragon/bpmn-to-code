package io.miragon.bpmn.benchmark

import io.miragon.bpmn.benchmark.Scenario.BpmnModelFile

/**
 * A Zeebe process of roughly [targetNodeCount] flow nodes for measuring how the generator scales. It repeats one block
 * with the element mix of real models: a service task with variables and a timer boundary event, an exclusive split
 * into a service and a user task, and a join. Like modeler output, it carries a diagram shape for every node and flow.
 */
class SyntheticProcess(private val targetNodeCount: Int) {

    private val processId = "synthetic$targetNodeCount"

    fun toModelFile(): BpmnModelFile {
        val blockCount = maxOf(1, (targetNodeCount - 2) / NODES_PER_BLOCK)
        val nodes = mutableListOf(Node(tag = "startEvent", id = START_ID, name = "Begin"))
        val flows = mutableListOf(Flow(id = "flow_start", source = START_ID, target = taskId(1, "a")))
        for (block in 1..blockCount) {
            val next = if (block < blockCount) taskId(block + 1, "a") else END_ID
            nodes += blockNodes(block)
            flows += blockFlows(block, next)
        }
        nodes += Node(tag = "endEvent", id = END_ID, name = "Done")
        return BpmnModelFile(name = processId, xml = definitions(nodes, flows))
    }

    private fun blockNodes(block: Int): List<Node> {
        val split = "split_$block"
        return listOf(
            Node(
                tag = "serviceTask",
                id = taskId(block, "a"),
                name = "Task $block A",
                extensions = taskDefinition("synthetic.job${block}a") + ioMapping(block),
            ),
            Node(
                tag = "exclusiveGateway",
                id = split,
                name = "Approved $block?",
                attributes = """ default="flow_${block}_split_c"""",
            ),
            Node(
                tag = "serviceTask",
                id = taskId(block, "b"),
                name = "Task $block B",
                extensions = taskDefinition("synthetic.job${block}b"),
            ),
            Node(tag = "userTask", id = taskId(block, "c"), name = "Task $block C"),
            Node(tag = "exclusiveGateway", id = "join_$block", name = null),
            Node(
                tag = "boundaryEvent",
                id = "timer_$block",
                name = "Timeout $block",
                attributes = """ attachedToRef="${taskId(block, "a")}"""",
                eventDefinition = timerDefinition(block),
            ),
            Node(tag = "endEvent", id = "timerEnd_$block", name = "Timed out $block"),
        )
    }

    private fun blockFlows(block: Int, next: String): List<Flow> {
        val split = "split_$block"
        val join = "join_$block"
        return listOf(
            Flow(id = "flow_${block}_a_split", source = taskId(block, "a"), target = split),
            Flow(
                id = "flow_${block}_split_b",
                source = split,
                target = taskId(block, "b"),
                condition = "=approved$block",
            ),
            Flow(id = "flow_${block}_split_c", source = split, target = taskId(block, "c")),
            Flow(id = "flow_${block}_b_join", source = taskId(block, "b"), target = join),
            Flow(id = "flow_${block}_c_join", source = taskId(block, "c"), target = join),
            Flow(id = "flow_${block}_join_next", source = join, target = next),
            Flow(id = "flow_${block}_timer_end", source = "timer_$block", target = "timerEnd_$block"),
        )
    }

    private fun definitions(nodes: List<Node>, flows: List<Flow>): String {
        val incoming = flows.groupBy({ it.target }, { it.id })
        val outgoing = flows.groupBy({ it.source }, { it.id })
        val process = buildString {
            nodes.forEach { append(it.toXml(incoming[it.id].orEmpty(), outgoing[it.id].orEmpty())) }
            flows.forEach { append(it.toXml()) }
        }
        val diagram = buildString {
            nodes.forEachIndexed { index, node -> append(shape(node.id, index)) }
            flows.forEachIndexed { index, flow -> append(edge(flow.id, index)) }
        }
        return """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<bpmn:definitions $NAMESPACES id="definitions_$processId" targetNamespace="http://bpmn.io/schema/bpmn">
            |  <bpmn:process id="$processId" name="Synthetic $targetNodeCount" isExecutable="true">
            |$process  </bpmn:process>
            |  <bpmndi:BPMNDiagram id="diagram_$processId">
            |    <bpmndi:BPMNPlane id="plane_$processId" bpmnElement="$processId">
            |$diagram    </bpmndi:BPMNPlane>
            |  </bpmndi:BPMNDiagram>
            |</bpmn:definitions>
            |
        """.trimMargin()
    }

    private fun taskId(block: Int, suffix: String) = "task_${block}_$suffix"

    private fun taskDefinition(type: String) = """        <zeebe:taskDefinition type="$type" />""" + "\n"

    private fun ioMapping(block: Int): String = """
        |        <zeebe:ioMapping>
        |          <zeebe:input source="=order$block" target="order$block" />
        |          <zeebe:output source="=result$block" target="result$block" />
        |        </zeebe:ioMapping>
        |
    """.trimMargin()

    private fun timerDefinition(block: Int): String = """
        |      <bpmn:timerEventDefinition id="timerDefinition_$block">
        |        <bpmn:timeDuration xsi:type="bpmn:tFormalExpression">PT1H</bpmn:timeDuration>
        |      </bpmn:timerEventDefinition>
        |
    """.trimMargin()

    private fun shape(id: String, index: Int): String {
        val x = SPACING * (index % ROW_LENGTH)
        val y = SPACING * (index / ROW_LENGTH)
        val bounds = """<dc:Bounds x="$x" y="$y" width="100" height="80" />"""
        return """      <bpmndi:BPMNShape id="${id}_di" bpmnElement="$id">$bounds</bpmndi:BPMNShape>""" + "\n"
    }

    private fun edge(id: String, index: Int): String {
        val x = SPACING * (index % ROW_LENGTH)
        val y = SPACING * (index / ROW_LENGTH) + 40
        val waypoints = """<di:waypoint x="$x" y="$y" /><di:waypoint x="${x + 50}" y="$y" />"""
        return """      <bpmndi:BPMNEdge id="${id}_di" bpmnElement="$id">$waypoints</bpmndi:BPMNEdge>""" + "\n"
    }

    private data class Node(
        val tag: String,
        val id: String,
        val name: String?,
        val attributes: String = "",
        val extensions: String = "",
        val eventDefinition: String = "",
    ) {
        fun toXml(incoming: List<String>, outgoing: List<String>): String = buildString {
            val nameAttribute = name?.let { """ name="$it"""" }.orEmpty()
            append("""    <bpmn:$tag id="$id"$nameAttribute$attributes>""").append('\n')
            if (extensions.isNotEmpty()) {
                append("      <bpmn:extensionElements>\n")
                append(extensions)
                append("      </bpmn:extensionElements>\n")
            }
            incoming.forEach { append("      <bpmn:incoming>$it</bpmn:incoming>\n") }
            outgoing.forEach { append("      <bpmn:outgoing>$it</bpmn:outgoing>\n") }
            append(eventDefinition)
            append("    </bpmn:$tag>\n")
        }
    }

    private data class Flow(val id: String, val source: String, val target: String, val condition: String? = null) {
        fun toXml(): String {
            val opening = """    <bpmn:sequenceFlow id="$id" sourceRef="$source" targetRef="$target""""
            if (condition == null) {
                return "$opening />\n"
            }
            return """
                |$opening>
                |      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">$condition</bpmn:conditionExpression>
                |    </bpmn:sequenceFlow>
                |
            """.trimMargin()
        }
    }

    private companion object {
        const val NODES_PER_BLOCK = 7
        const val START_ID = "startEvent_begin"
        const val END_ID = "endEvent_done"
        const val SPACING = 150
        const val ROW_LENGTH = 40
        const val NAMESPACES = "xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" " +
            "xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\" " +
            "xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\" " +
            "xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\" " +
            "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
            "xmlns:zeebe=\"http://camunda.org/schema/zeebe/1.0\" " +
            "xmlns:modeler=\"http://camunda.org/schema/modeler/1.0\" " +
            "modeler:executionPlatform=\"Camunda Cloud\" modeler:executionPlatformVersion=\"8.6.0\""
    }
}
