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
 * The end-to-end goldens compare text only, so the mapper path for both facets is asserted structurally here. The parity assertion is the point of the redesign: the normalised layer stays the same
 * across engines, and only the expressions differ.
 */
class ProcessJsonActivityFacetsTest {

    private val underTest = CreateProcessJsonInMemoryPlugin()

    @Test
    fun `multi-instance loop characteristics reach the json for every engine`() {
        // when
        val documents = bikeLeasingPerEngine()

        // then: sequential and the element binding are engine-independent
        documents.forEach { (engine, document) ->
            val loop = document.flowNode("serviceTask_issueInsurancePolicy")["multiInstance"]?.jsonObject
            assertThat(loop).describedAs("$engine multiInstance").isNotNull
            assertThat(loop?.text("sequential")).describedAs("$engine sequential").isEqualTo("true")
            assertThat(loop?.text("inputElement")).describedAs("$engine inputElement").isEqualTo("bikeId")
        }

        // and: a non-sequential loop is reported as such rather than omitted
        documents.forEach { (engine, document) ->
            val loop = document.flowNode("serviceTask_orderBike")["multiInstance"]?.jsonObject
            assertThat(loop?.text("sequential")).describedAs("$engine sequential").isEqualTo("false")
        }
    }

    @Test
    fun `io mappings reach the json for every engine`() {
        // when
        val documents = bikeLeasingPerEngine()

        // then: the parameter targets are normalised, the sources stay in the engine's own syntax
        documents.forEach { (engine, document) ->
            val ioMapping = document.flowNode("serviceTask_sendContract")["ioMapping"]?.jsonObject
            assertThat(ioMapping).describedAs("$engine ioMapping").isNotNull
            val targets = ioMapping?.get("outputs")?.jsonArray?.map { it.jsonObject.text("target") }
            assertThat(targets).describedAs("$engine output targets").containsExactly("contractId")
        }
    }

    @Test
    fun `the zeebe output collection binding is preserved verbatim`() {
        // given: only Zeebe models an output collection, so it is asserted on its own
        val document = bikeLeasingPerEngine().getValue(ProcessEngine.ZEEBE)

        // then
        val loop = document.flowNode("serviceTask_orderBike").getValue("multiInstance").jsonObject
        assertThat(loop.text("inputCollection")).isEqualTo("=bikeIds")
        assertThat(loop.text("outputCollection")).isEqualTo("orderIds")
        assertThat(loop.text("outputElement")).isEqualTo("=orderId")
    }

    @Test
    fun `activities without either facet omit both fields`() {
        // when
        val document = bikeLeasingPerEngine().getValue(ProcessEngine.ZEEBE)

        // then: absent facets are omitted rather than serialised as null or as an empty object
        val node = document.flowNode("serviceTask_issueInsurancePolicy")
        assertThat(node).doesNotContainKey("ioMapping")
        assertThat(document.flowNode("serviceTask_sendContract")).doesNotContainKey("multiInstance")
    }

    /**
     * The bike-leasing fixture — the one carrying both facets — generated for every engine.
     */
    private fun bikeLeasingPerEngine(): Map<ProcessEngine, JsonObject> = mapOf(
        ProcessEngine.ZEEBE to generate(ProcessEngine.ZEEBE, "zeebe/bike-leasing"),
        ProcessEngine.CAMUNDA_7 to generate(ProcessEngine.CAMUNDA_7, "c7/bike-leasing"),
        ProcessEngine.OPERATON to generate(ProcessEngine.OPERATON, "operaton/bike-leasing"),
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
        .flowNodesDeep().single { it.text("id") == id }

    private fun JsonObject.flowNodesDeep(): List<JsonObject> = this["flowNodes"]?.jsonArray.orEmpty()
        .map { it.jsonObject }.flatMap { listOf(it) + it.flowNodesDeep() }

    private fun JsonObject.text(field: String): String? = this[field]?.jsonPrimitive?.content

    private fun readResource(path: String): String = requireNotNull(javaClass.getResourceAsStream(path)) { "missing test resource $path" }
        .use { it.readBytes().decodeToString() }
}
