package io.miragon.bpmn.application.service

import io.miragon.bpmn.adapter.inbound.ExtractProcessModelsPlugin
import io.miragon.bpmn.application.port.inbound.ExtractProcessModelsUseCase
import io.miragon.bpmn.domain.BpmnResource
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.shared.TaskImplementation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.File

class ExtractProcessModelsServiceTest {

    private val underTest = ExtractProcessModelsService()

    @Test
    fun `extracts one model per resource, in the order they were given`() {
        // given: two BPMN files targeting the same engine
        val resources = listOf(resource("c8-subscribe-newsletter.bpmn"), resource("c8-send-newsletter.bpmn"))

        // when: extracting them
        val models = underTest.extractProcessModels(command(resources))

        // then: each resource yields its own model, and the order is preserved
        assertThat(models.map { it.processId }).containsExactly("newsletterSubscription", "sendNewsletter")
    }

    @Test
    fun `the models carry what the engine dialect resolved`() {
        // given: the Camunda 8 model
        val models = underTest.extractProcessModels(command(listOf(resource("c8-subscribe-newsletter.bpmn"))))

        // then: extraction really ran — a job type only the Zeebe dialect produces is present
        val implementations = models.single().allFlowNodes
            .filterIsInstance<FlowNodeDefinition.Activity.Task>()
            .mapNotNull { it.implementation }
        assertThat(implementations).contains(TaskImplementation.JobWorker("newsletter.sendConfirmationMail"))
    }

    @Test
    fun `no resources means no models`() {
        // when / then: an empty input is not an error
        assertThat(underTest.extractProcessModels(command(emptyList()))).isEmpty()
    }

    @Test
    fun `the plugin wires the use case by default`() {
        // when: going through the inbound entry point without injecting anything
        val models = ExtractProcessModelsPlugin().execute(
            listOf(resource("c8-subscribe-newsletter.bpmn")),
            ProcessEngine.ZEEBE,
        )

        // then
        assertThat(models.single().processId).isEqualTo("newsletterSubscription")
    }

    @Test
    fun `a broken resource fails with the file that caused it`() {
        // given: one good file and one that is not XML
        val resources = listOf(
            resource("c8-subscribe-newsletter.bpmn"),
            BpmnResource(fileName = "broken.bpmn", content = "<bpmn:definitions".toByteArray()),
        )

        // then: the failure names the offending file rather than the batch
        assertThatThrownBy { underTest.extractProcessModels(command(resources)) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("broken.bpmn")
    }

    private fun command(resources: List<BpmnResource>) = ExtractProcessModelsUseCase.Command(
        resources = resources,
        engine = ProcessEngine.ZEEBE,
    )

    private fun resource(fileName: String): BpmnResource {
        val url = requireNotNull(javaClass.getResource("/bpmn/$fileName"))
        return BpmnResource(fileName = fileName, content = File(url.toURI()).readBytes())
    }
}
