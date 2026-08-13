package io.miragon.bpmn.adapter.outbound.json

import io.miragon.bpmn.adapter.inbound.CreateProcessJsonInMemoryPlugin
import io.miragon.bpmn.domain.shared.ProcessEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Guards that the activity facets of [#73](https://github.com/Miragon/bpmn-to-code/issues/73) and
 * [#74](https://github.com/Miragon/bpmn-to-code/issues/74) survive all the way into the published JSON.
 *
 * The golden fixtures use the subscription process, which has neither facet, so the mapper path for both is
 * only covered here. The parity assertion is the point of the redesign: the normalised layer stays the same
 * across engines, and only the expressions differ.
 */
class ProcessJsonActivityFacetsTest {

    private val underTest = CreateProcessJsonInMemoryPlugin()

    @Test
    fun `multi-instance loop characteristics reach the json for every engine`() {
        // when
        val documents = sendNewsletterPerEngine()

        // then: sequential and the element binding are engine-independent
        documents.forEach { (engine, document) ->
            val loop = document.flowNode("serviceTask_sendToSubscriber")["multiInstance"]?.jsonObject
            assertThat(loop).describedAs("$engine multiInstance").isNotNull
            assertThat(loop?.text("sequential")).describedAs("$engine sequential").isEqualTo("true")
            assertThat(loop?.text("inputElement")).describedAs("$engine inputElement").isEqualTo("subscriber")
        }

        // and: a non-sequential loop is reported as such rather than omitted
        documents.forEach { (engine, document) ->
            val loop = document.flowNode("serviceTask_notifyAuthor")["multiInstance"]?.jsonObject
            assertThat(loop?.text("sequential")).describedAs("$engine sequential").isEqualTo("false")
        }
    }

    @Test
    fun `io mappings reach the json for every engine`() {
        // when
        val documents = sendNewsletterPerEngine()

        // then: the parameter targets are normalised, the sources stay in the engine's own syntax
        documents.forEach { (engine, document) ->
            val ioMapping = document.flowNode("serviceTask_loadSubscribers")["ioMapping"]?.jsonObject
            assertThat(ioMapping).describedAs("$engine ioMapping").isNotNull
            val targets = ioMapping?.get("outputs")?.jsonArray?.map { it.jsonObject.text("target") }
            assertThat(targets).describedAs("$engine output targets").containsExactly("subscribers", "author")
        }
    }

    @Test
    fun `the zeebe output collection binding is preserved verbatim`() {
        // given: only Zeebe models an output collection, so it is asserted on its own
        val document = sendNewsletterPerEngine().getValue(ProcessEngine.ZEEBE)

        // then
        val loop = document.flowNode("serviceTask_notifyAuthor").getValue("multiInstance").jsonObject
        assertThat(loop.text("inputCollection")).isEqualTo("=authors")
        assertThat(loop.text("outputCollection")).isEqualTo("results")
        assertThat(loop.text("outputElement")).isEqualTo("=result")
    }

    @Test
    fun `activities without either facet omit both fields`() {
        // when
        val document = sendNewsletterPerEngine().getValue(ProcessEngine.ZEEBE)

        // then: absent facets are omitted rather than serialised as null or as an empty object
        val node = document.flowNode("serviceTask_sendToSubscriber")
        assertThat(node).doesNotContainKey("ioMapping")
        assertThat(document.flowNode("serviceTask_loadSubscribers")).doesNotContainKey("multiInstance")
    }

    /**
     * The send-newsletter fixture — the only one carrying both facets — generated for every engine.
     */
    private fun sendNewsletterPerEngine(): Map<ProcessEngine, JsonObject> = mapOf(
        ProcessEngine.ZEEBE to generate(ProcessEngine.ZEEBE, "c8-send-newsletter"),
        ProcessEngine.CAMUNDA_7 to generate(ProcessEngine.CAMUNDA_7, "c7-send-newsletter"),
        ProcessEngine.OPERATON to generate(ProcessEngine.OPERATON, "operaton-send-newsletter"),
    )

    private fun generate(engine: ProcessEngine, fixture: String): JsonObject {
        val input = CreateProcessJsonInMemoryPlugin.BpmnInput(
            bpmnXml = readResource("/bpmn/$fixture.bpmn"),
            processName = fixture,
        )
        val generated = underTest.execute(bpmnContents = listOf(input), engine = engine).single()
        return Json.parseToJsonElement(generated.content).jsonObject
    }

    private fun JsonObject.flowNode(id: String): JsonObject = getValue("process").jsonObject
        .getValue("flowNodes").jsonArray
        .map { it.jsonObject }
        .single { it.text("id") == id }

    private fun JsonObject.text(field: String): String? = this[field]?.jsonPrimitive?.content

    private fun readResource(path: String): String = requireNotNull(javaClass.getResourceAsStream(path)) { "missing test resource $path" }
        .use { it.readBytes().decodeToString() }
}
