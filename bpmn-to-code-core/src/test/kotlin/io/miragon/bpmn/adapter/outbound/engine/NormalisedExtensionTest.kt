package io.miragon.bpmn.adapter.outbound.engine

import io.miragon.bpmn.adapter.outbound.engine.dialect.CamundaDialect
import io.miragon.bpmn.adapter.outbound.engine.dialect.ZeebeDialect
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.TaskImplementation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * `extensions` is the lossless escape hatch for engine XML we do **not** normalise (ADR 018, layer 3).
 * Re-emitting what a dialect already read into a typed field would state the same fact twice, so each
 * dialect declares the elements it reads in full and those are left out.
 */
class NormalisedExtensionTest {

    private companion object {
        const val CAMUNDA_7_NAMESPACE = "http://camunda.org/schema/1.0/bpmn"

        val TASK_HEADERS_BPMN = """
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" id="Definitions_1"
                              targetNamespace="http://bpmn.io/schema/bpmn">
              <bpmn:process id="headerProcess" isExecutable="true">
                <bpmn:serviceTask id="Task_1">
                  <bpmn:extensionElements>
                    <zeebe:taskDefinition type="worker" />
                    <zeebe:taskHeaders>
                      <zeebe:header key="resultVariable" value="order" />
                    </zeebe:taskHeaders>
                  </bpmn:extensionElements>
                </bpmn:serviceTask>
              </bpmn:process>
            </bpmn:definitions>
        """.trimIndent()

        val TWO_IMPLEMENTATIONS_BPMN = """
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:camunda="http://camunda.org/schema/1.0/bpmn" id="Definitions_2"
                              targetNamespace="http://bpmn.io/schema/bpmn">
              <bpmn:process id="raceProcess" isExecutable="true">
                <bpmn:serviceTask id="Task_1" camunda:topic="some-topic" camunda:class="com.example.Handler" />
              </bpmn:process>
            </bpmn:definitions>
        """.trimIndent()
    }

    @Test
    fun `zeebe extensions leave out the elements the dialect reads in full`() {
        // given: the Camunda 8 model, whose nodes carry ioMapping, taskDefinition and calledElement
        val model = extract(ZeebeDialect(), "c8-subscribe-newsletter")

        // when: looking at every extension the nodes kept
        val types = model.allFlowNodes.flatMap { node -> node.extensions.map { it.type } }

        // then: none of them restates a field the dialect already normalised
        assertThat(types).doesNotContain("zeebe:ioMapping", "zeebe:taskDefinition", "zeebe:calledElement")
    }

    @Test
    fun `the normalised fields still carry that information`() {
        // given: the same model
        val model = extract(ZeebeDialect(), "c8-subscribe-newsletter")

        // then: what was left out of extensions is present in typed form, so nothing was lost
        val task = model.allFlowNodes.single { it.id == "Activity_SendConfirmationMail" }
            as FlowNodeDefinition.Activity.Task
        assertThat(task.implementation?.reference).isEqualTo("newsletter.sendConfirmationMail")
        assertThat(task.ioMapping?.inputs?.map { it.target }).contains("subscriptionId")

        val callActivity = model.allFlowNodes.single { it.id == "CallActivity_AbortRegistration" }
            as FlowNodeDefinition.Activity.CallActivity
        assertThat(callActivity.definition.getValue()).isEqualTo("abort-registration")
    }

    @Test
    fun `zeebe extensions keep an element the dialect does not read`() {
        // given: a task carrying zeebe:taskHeaders, which has no normalised counterpart
        val model = ProcessModelReader(ZeebeDialect()).read(TASK_HEADERS_BPMN.toByteArray())

        // when: reading the task's extensions
        val extensions = model.allFlowNodes.single { it.id == "Task_1" }.extensions

        // then: the escape hatch still works — the raw element survives with its children
        val headers = extensions.single { it.type == "zeebe:taskHeaders" }
        assertThat(headers.children.single().attributes)
            .containsEntry("key", "resultVariable")
            .containsEntry("value", "order")
    }

    @Test
    fun `camunda extensions keep inputOutput because the dialect only reads part of it`() {
        // given: the Camunda 7 model
        val model = extract(CamundaDialect(CAMUNDA_7_NAMESPACE), "c7-subscribe-newsletter")

        // when: looking at the extensions
        val types = model.allFlowNodes.flatMap { node -> node.extensions.map { it.type } }

        // then: camunda:inputOutput stays — a nested camunda:script or camunda:map is not read into
        // IoMapping, so dropping the raw element would lose information
        assertThat(types).contains("camunda:inputOutput")
    }

    @Test
    fun `engine attributes leave out the one the dialect read`() {
        // given: the Camunda 7 model, whose compensation handler carries camunda:delegateExpression
        val model = extract(CamundaDialect(CAMUNDA_7_NAMESPACE), "c7-subscribe-newsletter")

        // when: looking at the node that has it
        val handler = model.allFlowNodes.single { it.id == "CompensationTask_DecrementSubscriptionCounter" }
            as FlowNodeDefinition.Activity.Task

        // then: the attribute is reported once, as a typed implementation
        assertThat(handler.implementation).isEqualTo(TaskImplementation.DelegateExpression("counterClass"))
        assertThat(handler.engineAttributes).doesNotContainKey("camunda:delegateExpression")
    }

    @Test
    fun `engine attributes keep the ones the dialect does not read`() {
        // given: the Camunda 7 model
        val model = extract(CamundaDialect(CAMUNDA_7_NAMESPACE), "c7-subscribe-newsletter")

        // when: looking at a node with execution attributes beyond the implementation
        val keys = model.allFlowNodes.flatMap { it.engineAttributes.keys }

        // then: async and exclusive have no typed counterpart, so they stay
        assertThat(keys).contains("camunda:asyncBefore", "camunda:exclusive")
    }

    @Test
    fun `an implementation attribute that lost the precedence race is still reported`() {
        // given: a task declaring both camunda:topic and camunda:class — only the topic wins
        val model = ProcessModelReader(CamundaDialect(CAMUNDA_7_NAMESPACE)).read(TWO_IMPLEMENTATIONS_BPMN.toByteArray())
        val task = model.allFlowNodes.single { it.id == "Task_1" } as FlowNodeDefinition.Activity.Task

        // then: the loser is not silently dropped — the raw layer is where it stays reachable
        assertThat(task.implementation).isEqualTo(TaskImplementation.ExternalTask("some-topic"))
        assertThat(task.engineAttributes).containsEntry("camunda:class", "com.example.Handler")
        assertThat(task.engineAttributes).doesNotContainKey("camunda:topic")
    }

    private fun extract(dialect: io.miragon.bpmn.adapter.outbound.engine.dialect.EngineDialect, fixture: String): ProcessModel {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/$fixture.bpmn"))
        return ProcessModelReader(dialect).read(File(resourceUrl.toURI()).readBytes())
    }
}
