package io.miragon.bpmn.adapter.outbound.json

import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.ProcessModel.Variant
import io.miragon.bpmn.domain.testSendNewsletterModel
import io.miragon.bpmn.domain.testSubscribeNewsletterModel
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.io.File

class BpmnJsonGeneratorTest {

    private val underTest = BpmnJsonGenerator()

    @Test
    fun `generates correct JSON for single model`() {
        // given: the subscribe newsletter BPMN model
        val model = testSubscribeNewsletterModel()

        // when: generating JSON
        val result = underTest.generate(model)

        // then: expect the generated JSON to match the expected snapshot
        val expectedFile = File(javaClass.getResource("/json/NewsletterSubscriptionProcess.json")!!.toURI())
        assertThat(result).isEqualToIgnoringWhitespace(expectedFile.readText())
        assertJsonSyntaxValid(result)
    }

    @Test
    fun `generates JSON with variants for merged model`() {
        // given: a merged model with a single variant
        val send = testSendNewsletterModel(variantName = "send")
        val merged = ProcessModel(
            processId = send.processId,
            flowNodes = send.flowNodes,
            definitions = send.definitions,
            variants = listOf(
                Variant("send", send.flowNodes, send.sequenceFlows),
            ),
        )

        // when: generating JSON
        val result = underTest.generate(merged)

        // then: expect the generated JSON to match the expected snapshot
        val expectedFile = File(javaClass.getResource("/json/MultiVariantNewsletterProcess.json")!!.toURI())
        assertThat(result).isEqualToIgnoringWhitespace(expectedFile.readText())
        assertJsonSyntaxValid(result)
    }

    @Test
    fun `adapter always uses processId as filename`() {
        // given: a model
        val model = testSubscribeNewsletterModel()
        val adapter = BpmnJsonGenerationAdapter()

        // when: generating JSON via adapter
        val result = adapter.generateJson(model)

        // then: filename is processId.json
        assertThat(result.fileName).isEqualTo("newsletterSubscription.json")
    }

    private fun assertJsonSyntaxValid(source: String) {
        runCatching { Json.parseToJsonElement(source) }
            .onFailure { fail("JSON syntax error in generated output: ${it.message}") }
    }
}
