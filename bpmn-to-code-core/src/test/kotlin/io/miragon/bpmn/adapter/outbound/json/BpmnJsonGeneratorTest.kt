package io.miragon.bpmn.adapter.outbound.json

import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.ProcessModel.Variant
import io.miragon.bpmn.domain.testBikeLeasingModel
import io.miragon.bpmn.domain.testCancelBikeOrderModel
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.io.File

class BpmnJsonGeneratorTest {

    private val underTest = BpmnJsonGenerator()

    @Test
    fun `generates correct JSON for single model`() {
        // given: the bike-leasing BPMN model
        val model = testBikeLeasingModel()

        // when: generating JSON
        val result = underTest.generate(model)

        // then: expect the generated JSON to match the expected snapshot
        assertThat(result).isEqualToIgnoringWhitespace(golden("/json/BikeLeasingProcess.json", result))
        assertJsonSyntaxValid(result)
    }

    @Test
    fun `generates JSON with variants for merged model`() {
        // given: a merged model with a single variant
        val retail = testCancelBikeOrderModel(variantName = "retail")
        val merged = ProcessModel(
            processId = retail.processId,
            flowNodes = retail.flowNodes,
            definitions = retail.definitions,
            variants = listOf(
                Variant(variantName = "retail", flowNodes = retail.flowNodes, sequenceFlows = retail.sequenceFlows),
            ),
        )

        // when: generating JSON
        val result = underTest.generate(merged)

        // then: expect the generated JSON to match the expected snapshot
        assertThat(result).isEqualToIgnoringWhitespace(golden("/json/MultiVariantCancelBikeOrderProcess.json", result))
        assertJsonSyntaxValid(result)
    }

    @Test
    fun `does not emit variables - they restate ioMapping and have no bpmn element`() {
        // given: a model whose nodes carry variables in the domain
        val model = testBikeLeasingModel()
        assertThat(model.variables).isNotEmpty()

        // when: generating JSON
        val result = underTest.generate(model)

        // then: the export never carries a variables facet
        assertThat(result).doesNotContain("\"variables\"")
    }

    @Test
    fun `emits isDefault only on the default sequence flow`() {
        // given: a model whose gateway has a default flow and a conditional sibling
        val model = testCancelBikeOrderModel()

        // when: generating JSON
        val result = underTest.generate(model)

        // then: the default flow carries isDefault, the conditional sibling does not
        assertThat(result).contains("\"id\": \"flow_cancellationPossibleToMergeReturn\"")
        assertThat(result).containsPattern("flow_cancellationPossibleToMergeReturn[\\s\\S]*?\"isDefault\": true")
        assertThat(result).doesNotContain("\"isDefault\": false")
    }

    @Test
    fun `adapter always uses processId as filename`() {
        // given: a model
        val model = testBikeLeasingModel()
        val adapter = BpmnJsonGenerationAdapter()

        // when: generating JSON via adapter
        val result = adapter.generateJson(model)

        // then: filename is processId.json
        assertThat(result.fileName).isEqualTo("bikeLeasing.json")
    }

    private fun golden(path: String, generated: String): String {
        if (System.getProperty("golden.update") == "true") {
            File("src/test/resources$path").writeText(generated)
            return generated
        }
        return File(requireNotNull(javaClass.getResource(path)).toURI()).readText()
    }

    private fun assertJsonSyntaxValid(source: String) {
        runCatching { Json.parseToJsonElement(source) }
            .onFailure { fail("JSON syntax error in generated output: ${it.message}") }
    }
}
