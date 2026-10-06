package io.miragon.bpmn.runtime.path

import io.miragon.bpmn.runtime.AbstractFlowNode
import io.miragon.bpmn.runtime.AssociatedCompensationHandler
import io.miragon.bpmn.runtime.AttachedBoundaryEvent
import io.miragon.bpmn.runtime.BpmnElementType
import io.miragon.bpmn.runtime.BpmnEventType
import io.miragon.bpmn.runtime.CompensationThrowEvent
import io.miragon.bpmn.runtime.ElementId
import io.miragon.bpmn.runtime.FlowScope
import io.miragon.bpmn.runtime.HasSuccessors
import io.miragon.bpmn.runtime.SequenceFlow
import io.miragon.bpmn.runtime.SequenceFlows
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
        val path = ProcessPath.from(Start).then { it.mid }.then { it.end }

        assertThat(path.ids).containsExactly("Start", "Mid", "End")
        assertThat(path.nodes.map { it.id.value }).containsExactly("Start", "Mid", "End")
        assertThat(path.current).isEqualTo(End)
    }

    @Test
    fun `then along a transition with one flow records the flow`() {
        val path = ProcessPath.from(Start).then { it.mid }

        assertThat(path.ids).containsExactly("Start", "Mid")
        assertThat(path.flowIds).containsExactly("flow_startToMid")
        assertThat(path.current).isEqualTo(Mid)
    }

    @Test
    fun `then along a transition with several flows records none of them`() {
        val path = ProcessPath.from(Start).then { it.mid }.then { it.end }

        assertThat(path.ids).containsExactly("Start", "Mid", "End")
        assertThat(path.flowIds).containsExactly("flow_startToMid")
    }

    @Test
    fun `then along a flow picked from a transition records exactly that flow`() {
        val path = ProcessPath.from(Start).then { it.mid }.then { next -> next.end.flows.single { it.conditionExpression == "=b" } }

        assertThat(path.ids).containsExactly("Start", "Mid", "End")
        assertThat(path.flowIds).containsExactly("flow_startToMid", "flow_midToEndB")
    }

    @Test
    fun `the flow of a transition with several flows is ambiguous`() {
        assertThat(runCatching { Mid.next.end.flow }.exceptionOrNull()).hasMessageContaining("pick one of `flows`")
    }

    @OptIn(RiskyNavigation::class)
    @Test
    fun `re-anchoring and walking an interior keep the flows recorded so far`() {
        val path = ProcessPath.from(Start)
            .then { it.mid }.jumpTo(Sub).inside { enter { it.innerStart } }.then { it.end }

        assertThat(path.ids).containsExactly("Start", "Mid", "InnerStart", "End")
        assertThat(path.flowIds).containsExactly("flow_startToMid", "flow_subToEnd")
    }

    @Test
    fun `thenMultipleTimes records the same node several times`() {
        val path = ProcessPath.from(Start).thenMultipleTimes(3) { it.mid }

        assertThat(path.ids).containsExactly("Start", "Mid", "Mid", "Mid")
        assertThat(path.distinctIds).containsExactly("Start", "Mid")
        assertThat(path.flowIds).containsExactly("flow_startToMid")
        assertThat(path.current).isEqualTo(Mid)
    }

    @Test
    fun `enter descends into the current node's interior and records the inner start`() {
        val path = ProcessPath.from(Sub).enter { it.innerStart }

        assertThat(path.ids).containsExactly("Sub", "InnerStart")
        assertThat(path.current).isEqualTo(InnerStart)
    }

    @Test
    fun `onto advances to the subprocess successor without recording it then enter descends`() {
        val path = ProcessPath.from(Start).onto { it.sub }.enter { it.innerStart }

        assertThat(path.ids).containsExactly("Start", "InnerStart")
        assertThat(path.current).isEqualTo(InnerStart)
    }

    @Test
    fun `enter with an explicit scope descends from any position`() {
        val path = ProcessPath.from(Start).enter(Sub) { it.innerStart }

        assertThat(path.ids).containsExactly("Start", "InnerStart")
    }

    @Test
    fun `interruptedBy leaves through a boundary event and records it`() {
        val path = ProcessPath.from(Sub).enter { it.innerStart }.interruptedBy(Sub) { it.boundary }

        assertThat(path.ids).containsExactly("Sub", "InnerStart", "Boundary")
        assertThat(path.current).isEqualTo(Boundary)
    }

    @Test
    fun `inside walks the interior in a block and returns to the subprocess to continue typed`() {
        val path = ProcessPath.from(Sub).inside { enter { it.innerStart } }.then { it.end }

        assertThat(path.ids).containsExactly("Sub", "InnerStart", "End")
        assertThat(path.current).isEqualTo(End)
    }

    @Test
    fun `onto then inside walks the subprocess interior and resumes on it`() {
        val path = ProcessPath.from(Start).onto { it.sub }.inside { enter { it.innerStart } }.then { it.end }

        assertThat(path.ids).containsExactly("Start", "InnerStart", "End")
        assertThat(path.flowIds).containsExactly("flow_startToSub", "flow_subToEnd")
        assertThat(path.current).isEqualTo(End)
    }

    @Test
    fun `interruptedBy through an attached boundary event records no flow`() {
        val path = ProcessPath.from(Start).onto { it.sub }.interruptedBy(Sub) { it.boundary }

        assertThat(path.ids).containsExactly("Start", "Boundary")
        assertThat(path.flowIds).containsExactly("flow_startToSub")
    }

    @Test
    fun `then onto the compensation handler of a boundary event records the handler and no flow`() {
        val path = ProcessPath.from(Start).onto { it.sub }.interruptedBy(Sub) { it.boundary }.then { it.handler }

        assertThat(path.ids).containsExactly("Start", "Boundary", "Handler")
        assertThat(path.flowIds).containsExactly("flow_startToSub")
        assertThat(path.current).isEqualTo(Handler)
    }

    @Test
    fun `throwingCompensation records the boundary event and its handler and stays on the current node`() {
        val path = ProcessPath.from(Throw).throwingCompensation(Boundary) { it.handler }.then { it.end }

        assertThat(path.ids).containsExactly("Throw", "Boundary", "Handler", "End")
        assertThat(path.flowIds).containsExactly("flow_throwToEnd")
    }

    @Test
    fun `throwingCompensation records the handler alone for an engine that does not report the boundary event`() {
        val path = ProcessPath.from(Throw).throwingCompensation(Boundary, includeBoundaryEvent = false) { it.handler }

        assertThat(path.ids).containsExactly("Throw", "Handler")
        assertThat(path.current).isEqualTo(Throw)
    }

    @Test
    fun `sequence flows need at least one flow`() {
        assertThat(runCatching { SequenceFlows(Mid, emptyList()) }.exceptionOrNull()).hasMessageContaining("at least one sequence flow")
    }

    @Test
    fun `nodesOf unions branches into a deduplicated set`() {
        val branchA = ProcessPath.from(Start).then { it.mid }.nodes
        val branchB = ProcessPath.from(Start).then { it.mid }.then { it.end }.nodes

        assertThat(nodesOf(branchA, branchB).map { it.id.value }).containsExactly("Start", "Mid", "End")
    }

    @OptIn(RiskyNavigation::class)
    @Test
    fun `jumpTo re-anchors without recording`() {
        val path = ProcessPath.from(Start).then { it.mid }.jumpTo(Start)

        assertThat(path.ids).containsExactly("Start", "Mid")
        assertThat(path.current).isEqualTo(Start)
    }

    private object End : AbstractFlowNode(ElementId("End"), BpmnElementType.END_EVENT)

    private object Throw : AbstractFlowNode(ElementId("Throw"), BpmnElementType.INTERMEDIATE_THROW_EVENT), HasSuccessors<Throw.Next>, CompensationThrowEvent {
        override val eventType: BpmnEventType = BpmnEventType.COMPENSATION
        override val next: Next get() = Next
        object Next {
            val end: SequenceFlows<End> get() = SequenceFlows.single(flowId = ElementId("flow_throwToEnd"), target = End)
        }
    }

    private object Handler : AbstractFlowNode(ElementId("Handler"), BpmnElementType.SERVICE_TASK)

    private object Boundary : AbstractFlowNode(ElementId("Boundary"), BpmnElementType.BOUNDARY_EVENT), HasSuccessors<Boundary.Next> {
        override val next: Next get() = Next
        object Next {
            val handler: AssociatedCompensationHandler<Handler> get() = AssociatedCompensationHandler(Handler)
        }
    }

    private object Mid : AbstractFlowNode(ElementId("Mid"), BpmnElementType.TASK), HasSuccessors<Mid.Next> {
        override val next: Next get() = Next
        object Next {
            val end: SequenceFlows<End> get() = SequenceFlows(
                End,
                SequenceFlow(id = ElementId("flow_midToEndA"), conditionExpression = "=a", target = End),
                SequenceFlow(id = ElementId("flow_midToEndB"), conditionExpression = "=b", target = End),
            )
        }
    }

    private object Start : AbstractFlowNode(ElementId("Start"), BpmnElementType.START_EVENT), HasSuccessors<Start.Next> {
        override val next: Next get() = Next
        object Next {
            val mid: SequenceFlows<Mid> get() = SequenceFlows.single(flowId = ElementId("flow_startToMid"), target = Mid)
            val sub: SequenceFlows<Sub> get() = SequenceFlows.single(flowId = ElementId("flow_startToSub"), target = Sub)
        }
    }

    private object InnerStart : AbstractFlowNode(ElementId("InnerStart"), BpmnElementType.START_EVENT)

    private object Sub : AbstractFlowNode(ElementId("Sub"), BpmnElementType.SUB_PROCESS), HasSuccessors<Sub.Next>, FlowScope<Sub.Start> {
        override val next: Next get() = Next
        override val startEvents: Start get() = Start
        object Next {
            val end: SequenceFlows<End> get() = SequenceFlows.single(flowId = ElementId("flow_subToEnd"), target = End)
            val boundary: AttachedBoundaryEvent<Boundary> get() = AttachedBoundaryEvent(Boundary)
        }
        object Start {
            val innerStart: InnerStart get() = InnerStart
        }
    }
}
