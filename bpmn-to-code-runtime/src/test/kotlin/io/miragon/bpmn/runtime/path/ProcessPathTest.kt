package io.miragon.bpmn.runtime.path

import io.miragon.bpmn.runtime.AbstractFlowNode
import io.miragon.bpmn.runtime.ElementId
import io.miragon.bpmn.runtime.HasInner
import io.miragon.bpmn.runtime.Navigable
import io.miragon.bpmn.runtime.NavigationScope
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
    fun `then with repeatTimes records the same node several times`() {
        val path = ProcessPath.from(Start)
            .then(repeatTimes = 3) { it.mid }

        assertThat(path.ids).containsExactly("Start", "Mid", "Mid", "Mid")
        assertThat(path.current).isEqualTo(Mid)
    }

    @Test
    fun `skip advances without recording`() {
        val path = ProcessPath.from(Start)
            .skip { it.mid }
            .then { it.end }

        assertThat(path.ids).containsExactly("Start", "End")
    }

    @Test
    fun `enter descends into the current node's interior and records the inner start`() {
        val path = ProcessPath.from(Sub)
            .enter { it.innerStart }

        assertThat(path.ids).containsExactly("Sub", "InnerStart")
        assertThat(path.current).isEqualTo(InnerStart)
    }

    @Test
    fun `enter with an explicit scope descends from any position`() {
        val path = ProcessPath.from(Start)
            .enter(Sub.Inner) { it.innerStart }

        assertThat(path.ids).containsExactly("Start", "InnerStart")
    }

    @Test
    fun `continueAfter re-anchors to the subprocess continuation and records the successor`() {
        val path = ProcessPath.from(Sub)
            .enter { it.innerStart }
            .continueAfter(Sub) { it.end }

        assertThat(path.ids).containsExactly("Sub", "InnerStart", "End")
        assertThat(path.current).isEqualTo(End)
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
            .inside {
                enter { it.innerStart }
            }
            .then { it.end }

        assertThat(path.ids).containsExactly("Sub", "InnerStart", "End")
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

    private object Mid : AbstractFlowNode(ElementId("Mid"), "TASK"), Navigable<Mid.Next> {
        override fun then(): Next = Next
        object Next {
            val end: End get() = End
        }
    }

    private object Start : AbstractFlowNode(ElementId("Start"), "START_EVENT"), Navigable<Start.Next> {
        override fun then(): Next = Next
        object Next {
            val mid: Mid get() = Mid
        }
    }

    private object InnerStart : AbstractFlowNode(ElementId("InnerStart"), "START_EVENT")

    private object Sub : AbstractFlowNode(ElementId("Sub"), "SUB_PROCESS"), Navigable<Sub.Next>, HasInner<Sub.Inner> {
        override fun then(): Next = Next
        override fun inner(): Inner = Inner
        object Next {
            val end: End get() = End
            val boundary: Boundary get() = Boundary
        }
        object Inner : NavigationScope<Inner.Next> {
            override fun then(): Next = Next
            object Next {
                val innerStart: InnerStart get() = InnerStart
            }
        }
    }
}
