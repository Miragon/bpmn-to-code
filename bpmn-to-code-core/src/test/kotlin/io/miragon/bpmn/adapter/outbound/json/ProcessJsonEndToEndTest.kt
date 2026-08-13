package io.miragon.bpmn.adapter.outbound.json

import io.miragon.bpmn.adapter.inbound.CreateProcessJsonInMemoryPlugin
import io.miragon.bpmn.domain.shared.ProcessEngine
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File

/**
 * Snapshots the **whole** pipeline: a real BPMN file in, the published JSON out.
 *
 * The other JSON snapshots ([BpmnJsonGeneratorTest]) build their model by hand, so they never see what an
 * engine dialect actually produces — no `extensions`, no `ioMapping`, no `engineAttributes`, and variables
 * without their expression. `ProcessJsonSchemaTest` does run real files through, but only checks shape and
 * reference integrity. Neither would notice a change in emitted *content*, which is how the raw
 * `zeebe:ioMapping` extensions came to restate `ioMapping` unnoticed.
 *
 * Regenerate with `-Dgolden.update=true` after reviewing the diff.
 */
class ProcessJsonEndToEndTest {

    private val underTest = CreateProcessJsonInMemoryPlugin()

    @ParameterizedTest
    @CsvSource(
        "ZEEBE, c8-subscribe-newsletter",
        "CAMUNDA_7, c7-subscribe-newsletter",
        "OPERATON, operaton-subscribe-newsletter",
    )
    fun `real bpmn produces the committed json`(engine: ProcessEngine, fixture: String) {
        // given: the shared fixture for this engine
        val input = CreateProcessJsonInMemoryPlugin.BpmnInput(
            bpmnXml = readResource("/bpmn/$fixture.bpmn"),
            processName = fixture,
        )

        // when: running the real extraction and JSON generation
        val generated = underTest.execute(listOf(input), engine).single().content

        // then: it matches the committed snapshot byte for byte
        assertThat(generated).isEqualToIgnoringWhitespace(golden(fixture, generated))
    }

    private fun golden(fixture: String, generated: String): String {
        val path = "/json/e2e/$fixture.json"
        if (System.getProperty("golden.update") == "true") {
            File("src/test/resources$path").apply { parentFile.mkdirs() }.writeText(generated)
            return generated
        }
        return readResource(path)
    }

    private fun readResource(path: String): String = requireNotNull(javaClass.getResourceAsStream(path)) { "missing resource $path" }
        .bufferedReader()
        .readText()
}
