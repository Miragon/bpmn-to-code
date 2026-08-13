package io.miragon.bpmn.adapter.outbound.json

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import io.miragon.bpmn.adapter.inbound.CreateProcessJsonInMemoryPlugin
import io.miragon.bpmn.domain.shared.ProcessEngine
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Guards the published process-JSON contract (ADR 018).
 *
 * Every generated file points at `docs/public/schema/process-model/2.0.json` via `$schema`, so the schema
 * and the emitted output have to stay in step. The schema is validated from the classpath rather than the
 * published URL, so the test runs offline.
 *
 * JSON Schema only checks *shape*. The reference-integrity test below covers the part it cannot see —
 * that `incoming` / `outgoing` really hold sequence-flow ids of the same scope, and that every `…Ref`
 * resolves into `definitions`. Without it a regression back to node-id relations would pass unnoticed.
 */
class ProcessJsonSchemaTest {

    private val underTest = CreateProcessJsonInMemoryPlugin()

    private val mapper = ObjectMapper()

    private val schema = JsonSchemaFactory
        .getInstance(SpecVersion.VersionFlag.V202012)
        .getSchema(requireNotNull(javaClass.getResourceAsStream("/process-model/2.0.json")))

    @ParameterizedTest
    @EnumSource(ProcessEngine::class)
    fun `generated process json conforms to the published schema`(engine: ProcessEngine) {
        // when: every shared fixture of this engine runs through the real pipeline
        val generated = generateAll(engine)

        // then: no fixture produces output the published schema rejects
        assertThat(generated).isNotEmpty
        generated.forEach { (fixture, json) ->
            val violations = schema.validate(mapper.readTree(json)).map { "${it.instanceLocation}: ${it.message}" }
            assertThat(violations).describedAs(fixture).isEmpty()
        }
    }

    @Test
    fun `golden json fixtures conform to the published schema`() {
        // given: the committed fixtures, which also cover the merged multi-variant shape
        val goldenFiles = listOf(
            "/json/NewsletterSubscriptionProcess.json",
            "/json/MultiVariantNewsletterProcess.json",
            "/json/e2e/c8-subscribe-newsletter.json",
            "/json/e2e/c7-subscribe-newsletter.json",
            "/json/e2e/operaton-subscribe-newsletter.json",
        )

        // then
        goldenFiles.forEach { path ->
            val violations = schema.validate(mapper.readTree(readResource(path))).map { it.message }
            assertThat(violations).describedAs(path).isEmpty()
        }
    }

    @ParameterizedTest
    @EnumSource(ProcessEngine::class)
    fun `every reference in the generated json resolves`(engine: ProcessEngine) {
        // when
        val generated = generateAll(engine)

        // then: relations point at sequence flows of their own scope, and every ...Ref resolves
        generated.forEach { (fixture, json) ->
            val document = mapper.readTree(json)
            val declaredIds = document.definitionIds()
            document.scopes().forEach { scope ->
                val flowIds = scope["sequenceFlows"].idsOf()
                scope["flowNodes"].forEach { node ->
                    assertThat(node.stringsAt("incoming") + node.stringsAt("outgoing"))
                        .describedAs("$fixture / ${node["id"].asText()} relations")
                        .isSubsetOf(flowIds)
                    assertThat(node.referencedDefinitionIds())
                        .describedAs("$fixture / ${node["id"].asText()} references")
                        .isSubsetOf(declaredIds)
                }
            }
        }
    }

    @Test
    fun `a message correlation key is declared once, on the message it belongs to`() {
        // given: a Zeebe process whose zeebe:subscription sits on the bpmn:Message root element
        val input = CreateProcessJsonInMemoryPlugin.BpmnInput(
            bpmnXml = readResource("/bpmn/c8-subscribe-newsletter.bpmn"),
            processName = "c8-subscribe-newsletter",
        )
        val generated = underTest.execute(listOf(input), ProcessEngine.ZEEBE).single()

        // then: it is a property of the entity, not repeated on every referencing event
        val document = mapper.readTree(generated.content)
        val message = document.at("/definitions/messages").single { it["name"].asText() == "Message_SubscriptionConfirmed" }
        assertThat(message["correlationKey"].asText()).isEqualTo("=subscriptionId")
        assertThat(generated.content).doesNotContain("\"subscription\"")
    }

    /**
     * Each fixture of [engine] run through the real pipeline, paired with its fixture name.
     */
    private fun generateAll(engine: ProcessEngine): List<Pair<String, String>> = fixturesFor(engine).flatMap { fixture ->
        val input = CreateProcessJsonInMemoryPlugin.BpmnInput(
            bpmnXml = readResource("/bpmn/$fixture.bpmn"),
            processName = fixture,
        )
        underTest.execute(bpmnContents = listOf(input), engine = engine).map { fixture to it.content }
    }

    /**
     * The process scope plus every variant and every nested sub-process scope.
     */
    private fun JsonNode.scopes(): List<JsonNode> {
        val roots = listOf(this["process"]) + (this["variants"]?.toList() ?: emptyList())
        return roots.flatMap { it.withNestedScopes() }
    }

    private fun JsonNode.withNestedScopes(): List<JsonNode> {
        val children = this["flowNodes"]?.filter { it.has("flowNodes") } ?: emptyList()
        return listOf(this) + children.flatMap { it.withNestedScopes() }
    }

    private fun JsonNode.definitionIds(): List<String> {
        val definitions = this["definitions"] ?: return emptyList()
        return definitions.flatMap { group -> group.idsOf() }
    }

    private fun JsonNode.referencedDefinitionIds(): List<String> {
        val fromEvents = this["eventDefinitions"]?.flatMap { definition ->
            referenceFields.mapNotNull { field -> definition[field]?.asText() }
        } ?: emptyList()
        return fromEvents + listOfNotNull(this["messageRef"]?.asText())
    }

    private fun JsonNode?.idsOf(): List<String> = this?.map { it["id"].asText() } ?: emptyList()

    private fun JsonNode.stringsAt(field: String): List<String> = this[field]?.map { it.asText() } ?: emptyList()

    private fun readResource(path: String): String = requireNotNull(javaClass.getResourceAsStream(path)) { "missing test resource $path" }
        .use { it.readBytes().decodeToString() }

    private fun fixturesFor(engine: ProcessEngine): List<String> = when (engine) {
        ProcessEngine.ZEEBE -> zeebeFixtures
        ProcessEngine.CAMUNDA_7 -> camunda7Fixtures
        ProcessEngine.OPERATON -> operatonFixtures
    }

    private val referenceFields = listOf(
        "messageRef",
        "signalRef",
        "errorRef",
        "escalationRef",
    )

    private val zeebeFixtures = listOf(
        "c8-subscribe-newsletter",
        "c8-send-newsletter",
        "c8-non-executable",
    )

    private val camunda7Fixtures = listOf(
        "c7-subscribe-newsletter",
        "c7-send-newsletter",
        "c7-additional-variables",
        "c7-non-executable",
        "c7-no-executable-attr",
    )

    private val operatonFixtures = listOf(
        "operaton-subscribe-newsletter",
        "operaton-send-newsletter",
        "operaton-additional-variables",
        "operaton-non-executable",
    )
}
