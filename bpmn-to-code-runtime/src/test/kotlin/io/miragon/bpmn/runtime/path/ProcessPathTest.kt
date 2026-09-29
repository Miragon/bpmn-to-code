package io.miragon.bpmn.runtime.path

import io.miragon.bpmn.runtime.AbstractFlowNode
import io.miragon.bpmn.runtime.ElementId
import io.miragon.bpmn.runtime.FlowScope
import io.miragon.bpmn.runtime.HasOutgoingFlows
import io.miragon.bpmn.runtime.HasSuccessors
import io.miragon.bpmn.runtime.SequenceFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit test of the [ProcessPath] step operators over a tiny hand-built graph, so the builder mechanics are
 * covered in the runtime module itself. The integration test over a real generated API lives alongside in
 * [ProcessPathIntegrationTest].
 */
class ProcessPathTest {

    @Test
    fun `then records each hop and exposes ids and nodes in order`() {
        val path = ProcessPath.from(Start)
            .then { it.mid }
            .then { it.end }

        assertThat(path.ids).containsExactly("Start", "Mid", "End")
        assertThat(path.nodes.map { it.id.value }).containsExactly("Start", "Mid", "End")
        assertThat(path.current).isEqualTo(End)
    }

    @Test
    fun `via walks a chosen outgoing sequence flow and records its target and the flow`() {
        val path = ProcessPath.from(Start)
            .via { it.toMid }

        assertThat(path.ids).containsExactly("Start", "Mid")
        assertThat(path.flowIds).containsExactly("flow_startToMid")
        assertThat(path.current).isEqualTo(Mid)
    }

    @Test
    fun `then records no sequence flow and later steps keep the flows recorded so far`() {
        val path = ProcessPath.from(Start)
            .via { it.toMid }
            .then { it.end }

        assertThat(path.ids).containsExactly("Start", "Mid", "End")
        assertThat(path.flowIds).containsExactly("flow_startToMid")
    }

    @OptIn(RiskyNavigation::class)
    @Test
    fun `re-anchoring and walking an interior keep the flows recorded so far`() {
        val path = ProcessPath.from(Start)
            .via { it.toMid }
            .jumpTo(Sub)
            .inside { enter { it.innerStart } }
            .then { it.end }

        assertThat(path.ids).containsExactly("Start", "Mid", "InnerStart", "End")
        assertThat(path.flowIds).containsExactly("flow_startToMid")
    }

    @Test
    fun `thenMultipleTimes records the same node several times`() {
        val path = ProcessPath.from(Start)
            .thenMultipleTimes(3) { it.mid }

        assertThat(path.ids).containsExactly("Start", "Mid", "Mid", "Mid")
        assertThat(path.distinctIds).containsExactly("Start", "Mid")
        assertThat(path.current).isEqualTo(Mid)
    }

    @Test
    fun `enter descends into the current node's interior and records the inner start`() {
        val path = ProcessPath.from(Sub)
            .enter { it.innerStart }

        assertThat(path.ids).containsExactly("Sub", "InnerStart")
        assertThat(path.current).isEqualTo(InnerStart)
    }

    @Test
    fun `onto advances to the subprocess successor without recording it then enter descends`() {
        val path = ProcessPath.from(Start)
            .onto { it.sub }
            .enter { it.innerStart }

        assertThat(path.ids).containsExactly("Start", "InnerStart")
        assertThat(path.current).isEqualTo(InnerStart)
    }

    @Test
    fun `enter with an explicit scope descends from any position`() {
        val path = ProcessPath.from(Start)
            .enter(Sub) { it.innerStart }

        assertThat(path.ids).containsExactly("Start", "InnerStart")
    }

    @Test
    fun `interruptedBy leaves through a boundary event and records it`() {
        val path = ProcessPath.from(Sub)
            .enter { it.innerStart }
            .interruptedBy(Sub) { it.boundary }

        assertThat(path.ids).containsExactly("Sub", "InnerStart", "Boundary")
        assertThat(path.current).isEqualTo(Boundary)
    }

    @Test
    fun `inside walks the interior in a block and returns to the subprocess to continue typed`() {
        val path = ProcessPath.from(Sub)
            .inside { enter { it.innerStart } }
            .then { it.end }

        assertThat(path.ids).containsExactly("Sub", "InnerStart", "End")
        assertThat(path.current).isEqualTo(End)
    }

    @Test
    fun `onto then inside walks the subprocess interior and resumes on it`() {
        val path = ProcessPath.from(Start)
            .onto { it.sub }
            .inside { enter { it.innerStart } }
            .then { it.end }

        assertThat(path.ids).containsExactly("Start", "InnerStart", "End")
        assertThat(path.current).isEqualTo(End)
    }

    @Test
    fun `nodesOf unions branches into a deduplicated set`() {
        val branchA = ProcessPath.from(Start).then { it.mid }.nodes
        val branchB = ProcessPath.from(Start).then { it.mid }.then { it.end }.nodes

        assertThat(nodesOf(branchA, branchB).map { it.id.value })
            .containsExactly("Start", "Mid", "End")
    }

    @OptIn(RiskyNavigation::class)
    @Test
    fun `jumpTo re-anchors without recording`() {
        val path = ProcessPath.from(Start)
            .then { it.mid }
            .jumpTo(Start)

        assertThat(path.ids).containsExactly("Start", "Mid")
        assertThat(path.current).isEqualTo(Start)
    }

    private object End : AbstractFlowNode(ElementId("End"), "END_EVENT")

    private object Boundary : AbstractFlowNode(ElementId("Boundary"), "TIMER_BOUNDARY_EVENT")

    private object Mid : AbstractFlowNode(ElementId("Mid"), "TASK"), HasSuccessors<Mid.Next> {
        override val next: Next get() = Next
        object Next {
            val end: End get() = End
        }
    }

    private object Start : AbstractFlowNode(ElementId("Start"), "START_EVENT"), HasSuccessors<Start.Next>, HasOutgoingFlows<Start.OutgoingFlows> {
        override val next: Next get() = Next
        override val outgoingFlows: OutgoingFlows get() = OutgoingFlows
        object Next {
            val mid: Mid get() = Mid
            val sub: Sub get() = Sub
        }
        object OutgoingFlows {
            val toMid: SequenceFlow<Mid> get() = SequenceFlow(ElementId("flow_startToMid"), null, null, false, Mid)
        }
    }

    private object InnerStart : AbstractFlowNode(ElementId("InnerStart"), "START_EVENT")

    private object Sub : AbstractFlowNode(ElementId("Sub"), "SUB_PROCESS"), HasSuccessors<Sub.Next>, FlowScope<Sub.Start> {
        override val next: Next get() = Next
        override val startEvents: Start get() = Start
        object Next {
            val end: End get() = End
            val boundary: Boundary get() = Boundary
        }
        object Start {
            val innerStart: InnerStart get() = InnerStart
        }
    }
}
