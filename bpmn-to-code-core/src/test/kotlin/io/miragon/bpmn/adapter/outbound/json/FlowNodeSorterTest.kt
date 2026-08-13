package io.miragon.bpmn.adapter.outbound.json

import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.GatewayKind
import io.miragon.bpmn.domain.shared.SequenceFlowDefinition
import io.miragon.bpmn.domain.shared.SubProcessKind
import io.miragon.bpmn.domain.shared.TaskKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FlowNodeSorterTest {

    // Adjacency is now derived from sequence flows: a node's incoming/outgoing hold sequence-flow ids, and
    // each flow maps its id to a target node. These helpers wire nodes to a shared flow list built from edges.

    private fun edges(vararg pairs: Pair<String, String>): List<SequenceFlowDefinition> = pairs.map { (source, target) ->
        SequenceFlowDefinition(id = "$source->$target", sourceRef = source, targetRef = target)
    }

    private fun outgoingOf(id: String, flows: List<SequenceFlowDefinition>): List<String> = flows.filter { it.sourceRef == id }.map { it.id!! }

    private fun incomingOf(id: String, flows: List<SequenceFlowDefinition>): List<String> = flows.filter { it.targetRef == id }.map { it.id!! }

    private fun task(id: String, flows: List<SequenceFlowDefinition>): FlowNodeDefinition = FlowNodeDefinition.Activity.Task(
        id = id,
        kind = TaskKind.SERVICE,
        incoming = incomingOf(id, flows),
        outgoing = outgoingOf(id, flows),
    )

    private fun event(
        id: String,
        shape: EventShape,
        flows: List<SequenceFlowDefinition>,
        attachedToRef: String? = null,
    ): FlowNodeDefinition = FlowNodeDefinition.Event(
        id = id,
        shape = shape,
        incoming = incomingOf(id, flows),
        outgoing = outgoingOf(id, flows),
        attachedToRef = attachedToRef,
    )

    private fun gateway(id: String, kind: GatewayKind, flows: List<SequenceFlowDefinition>): FlowNodeDefinition = FlowNodeDefinition.Gateway(
        id = id,
        kind = kind,
        incoming = incomingOf(id, flows),
        outgoing = outgoingOf(id, flows),
    )

    @Test
    fun `linear chain is sorted start to end`() {
        // given: a linear start → task → end chain
        val flows = edges("Start" to "Task", "Task" to "End")
        val start = event("Start", EventShape.START_EVENT, flows)
        val task = task("Task", flows)
        val end = event("End", EventShape.END_EVENT, flows)

        // when: sorting the unsorted list
        val result = FlowNodeSorter.sort(listOf(task, end, start), flows)

        // then: nodes appear in process order
        assertThat(result.map { it.id }).containsExactly("Start", "Task", "End")
    }

    @Test
    fun `start events are visited before other top-level nodes`() {
        // given: two start events feeding the same task
        val flows = edges("Start_A" to "Task", "Start_B" to "Task", "Task" to "End")
        val startA = event("Start_A", EventShape.START_EVENT, flows)
        val startB = event("Start_B", EventShape.START_EVENT, flows)
        val task = task("Task", flows)
        val end = event("End", EventShape.END_EVENT, flows)

        // when: sorting
        val result = FlowNodeSorter.sort(listOf(task, end, startB, startA), flows)
        val ids = result.map { it.id }

        // then: Start_A (alphabetically first) leads, all nodes appear exactly once
        assertThat(ids.first()).isEqualTo("Start_A")
        assertThat(ids).containsExactlyInAnyOrder("Start_A", "Start_B", "Task", "End")
    }

    @Test
    fun `boundary event appears after its parent`() {
        // given: a task with an attached boundary event
        val flows = edges("Start" to "Task", "Task" to "End", "Boundary" to "ErrorEnd")
        val start = event("Start", EventShape.START_EVENT, flows)
        val task = task("Task", flows)
        val boundary = event("Boundary", EventShape.BOUNDARY_EVENT, flows, attachedToRef = "Task")
        val end = event("End", EventShape.END_EVENT, flows)
        val errorEnd = event("ErrorEnd", EventShape.END_EVENT, flows)

        // when: sorting
        val result = FlowNodeSorter.sort(listOf(end, errorEnd, boundary, task, start), flows)
        val ids = result.map { it.id }

        // then: Boundary comes after Task, ErrorEnd comes after Boundary
        assertThat(ids.indexOf("Boundary")).isGreaterThan(ids.indexOf("Task"))
        assertThat(ids.indexOf("ErrorEnd")).isGreaterThan(ids.indexOf("Boundary"))
    }

    @Test
    fun `subprocess is ordered in its scope and its children are sorted separately`() {
        // given: a top-level scope containing a sub-process. Sub-process children are no longer inlined into
        // the parent scope — they live inside the sub-process node and are sorted by re-applying the sorter.
        val topFlows = edges("Start" to "Sub", "Sub" to "End")
        val start = event("Start", EventShape.START_EVENT, topFlows)
        val childFlows = edges("SubStart" to "SubTask", "SubTask" to "SubEnd")
        val subStart = event("SubStart", EventShape.START_EVENT, childFlows)
        val subTask = task("SubTask", childFlows)
        val subEnd = event("SubEnd", EventShape.END_EVENT, childFlows)
        val sub = FlowNodeDefinition.Activity.SubProcess(
            id = "Sub",
            kind = SubProcessKind.PLAIN,
            incoming = incomingOf("Sub", topFlows),
            outgoing = outgoingOf("Sub", topFlows),
            flowNodes = listOf(subEnd, subTask, subStart),
            sequenceFlows = childFlows,
        )
        val end = event("End", EventShape.END_EVENT, topFlows)

        // when: sorting the top scope
        val topResult = FlowNodeSorter.sort(listOf(end, sub, start), topFlows)

        // then: the sub-process is ordered between start and end, without its children leaking into the scope
        assertThat(topResult.map { it.id }).containsExactly("Start", "Sub", "End")

        // and when: sorting the sub-process's own scope
        val childResult = FlowNodeSorter.sort(sub.flowNodes, sub.sequenceFlows)

        // then: its children appear in process order
        assertThat(childResult.map { it.id }).containsExactly("SubStart", "SubTask", "SubEnd")
    }

    @Test
    fun `cycles do not cause infinite loops`() {
        // given: a cyclic A ↔ B loop
        val flows = edges("Start" to "A", "A" to "B", "B" to "A", "B" to "End")
        val start = event("Start", EventShape.START_EVENT, flows)
        val a = task("A", flows)
        val b = task("B", flows)
        val end = event("End", EventShape.END_EVENT, flows)

        // when: sorting
        val result = FlowNodeSorter.sort(listOf(b, a, end, start), flows)

        // then: no exception; each node appears exactly once
        assertThat(result.map { it.id }).containsExactlyInAnyOrder("Start", "A", "B", "End")
        assertThat(result).hasSize(4)
    }

    @Test
    fun `already sorted input is idempotent`() {
        // given: nodes already in correct order
        val flows = edges("Start" to "Task", "Task" to "End")
        val start = event("Start", EventShape.START_EVENT, flows)
        val task = task("Task", flows)
        val end = event("End", EventShape.END_EVENT, flows)

        // when: sorting
        val result = FlowNodeSorter.sort(listOf(start, task, end), flows)

        // then: order is unchanged
        assertThat(result.map { it.id }).containsExactly("Start", "Task", "End")
    }

    @Test
    fun `exclusive gateway branches appear after gateway`() {
        // given: a gateway splitting into two branches
        val flows = edges(
            "Start" to "GW",
            "GW" to "Branch_A",
            "GW" to "Branch_B",
            "Branch_A" to "End",
            "Branch_B" to "End",
        )
        val start = event("Start", EventShape.START_EVENT, flows)
        val gw = gateway("GW", GatewayKind.EXCLUSIVE, flows)
        val branchA = task("Branch_A", flows)
        val branchB = task("Branch_B", flows)
        val end = event("End", EventShape.END_EVENT, flows)

        // when: sorting
        val result = FlowNodeSorter.sort(listOf(end, branchB, gw, branchA, start), flows)
        val ids = result.map { it.id }

        // then: both branches appear after their gateway
        assertThat(ids.indexOf("GW")).isGreaterThan(ids.indexOf("Start"))
        assertThat(ids.indexOf("Branch_A")).isGreaterThan(ids.indexOf("GW"))
        assertThat(ids.indexOf("Branch_B")).isGreaterThan(ids.indexOf("GW"))
    }
}
