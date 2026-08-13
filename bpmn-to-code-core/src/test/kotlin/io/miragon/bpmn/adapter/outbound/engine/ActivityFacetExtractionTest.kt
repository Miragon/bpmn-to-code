package io.miragon.bpmn.adapter.outbound.engine

import io.miragon.bpmn.adapter.outbound.engine.dialect.CamundaDialect
import io.miragon.bpmn.adapter.outbound.engine.dialect.ZeebeDialect
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.IoMapping
import io.miragon.bpmn.domain.shared.MultiInstanceDefinition
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Guards the two activity facets the v2 model introduced: multi-instance loop characteristics
 * ([#73](https://github.com/Miragon/bpmn-to-code/issues/73)) and I/O mappings
 * ([#74](https://github.com/Miragon/bpmn-to-code/issues/74)).
 *
 * Each engine spells them differently — `zeebe:loopCharacteristics` / `zeebe:ioMapping` versus
 * `camunda:collection` / `camunda:inputOutput` — but they normalise onto the same domain shape. Only the
 * expressions themselves stay engine-specific, because they are preserved verbatim (FEEL `=subscribers`,
 * JUEL `${'$'}{subscribers}`, plain `subscribers`).
 */
class ActivityFacetExtractionTest {

    @Test
    fun `zeebe extract reads multi-instance loop characteristics`() {

        // given
        val model = extract(ProcessModelReader(ZeebeDialect()), "c8-send-newsletter")

        // then: isSequential comes from BPMN, the collection bindings from zeebe:loopCharacteristics
        assertThat(model.multiInstanceOf("serviceTask_sendToSubscriber")).isEqualTo(
            MultiInstanceDefinition(
                sequential = true,
                inputCollection = "=subscribers",
                inputElement = "subscriber",
            )
        )
        assertThat(model.multiInstanceOf("serviceTask_notifyAuthor")).isEqualTo(
            MultiInstanceDefinition(
                sequential = false,
                inputCollection = "=authors",
                inputElement = "author",
                outputCollection = "results",
                outputElement = "=result",
            )
        )
    }

    @Test
    fun `zeebe extract reads io mappings`() {

        // given
        val model = extract(ProcessModelReader(ZeebeDialect()), "c8-send-newsletter")

        // then: zeebe:input and zeebe:output keep source and target verbatim
        assertThat(model.ioMappingOf("serviceTask_loadSubscribers")).isEqualTo(
            IoMapping(
                outputs = listOf(
                    IoMapping.Parameter(target = "subscribers", source = "=subscribers"),
                    IoMapping.Parameter(target = "author", source = "=author"),
                )
            )
        )
        assertThat(model.ioMappingOf("serviceTask_publishNewsletter")).isEqualTo(
            IoMapping(
                inputs = listOf(
                    IoMapping.Parameter(target = "method", source = "POST"),
                    IoMapping.Parameter(target = "url", source = "https://api.example.com/newsletter"),
                ),
                outputs = listOf(IoMapping.Parameter(target = "apiResponse", source = "=response")),
            )
        )
    }

    @Test
    fun `camunda 7 extract reads multi-instance loop characteristics`() {

        // given
        val model = extract(ProcessModelReader(CamundaDialect(CAMUNDA_7_NAMESPACE)), "c7-send-newsletter")

        // then: camunda:collection and camunda:elementVariable normalise onto the same fields as Zeebe
        assertThat(model.multiInstanceOf("serviceTask_sendToSubscriber")).isEqualTo(
            MultiInstanceDefinition(
                sequential = true,
                inputCollection = "\${subscribers}",
                inputElement = "subscriber",
            )
        )
        assertThat(model.multiInstanceOf("serviceTask_notifyAuthor")).isEqualTo(
            MultiInstanceDefinition(
                sequential = false,
                inputCollection = "\${authors}",
                inputElement = "author",
            )
        )
    }

    @Test
    fun `camunda 7 extract reads io mappings`() {

        // given
        val model = extract(ProcessModelReader(CamundaDialect(CAMUNDA_7_NAMESPACE)), "c7-send-newsletter")

        // then: the parameter name becomes the target, the element body the source
        assertThat(model.ioMappingOf("serviceTask_loadSubscribers")).isEqualTo(
            IoMapping(
                outputs = listOf(
                    IoMapping.Parameter(target = "subscribers", source = "\${subscribers}"),
                    IoMapping.Parameter(target = "author", source = "\${author}"),
                )
            )
        )
        assertThat(model.ioMappingOf("serviceTask_notifyAuthor")).isEqualTo(
            IoMapping(inputs = listOf(IoMapping.Parameter(target = "test", source = "null")))
        )
    }

    @Test
    fun `operaton extract reads multi-instance loop characteristics`() {

        // given
        val model = extract(ProcessModelReader(CamundaDialect(OPERATON_NAMESPACE)), "operaton-send-newsletter")

        // then: the operaton namespace carries the identical vocabulary (ADR 010)
        assertThat(model.multiInstanceOf("serviceTask_sendToSubscriber")).isEqualTo(
            MultiInstanceDefinition(
                sequential = true,
                inputCollection = "subscribers",
                inputElement = "subscriber",
            )
        )
        assertThat(model.multiInstanceOf("serviceTask_notifyAuthor")).isEqualTo(
            MultiInstanceDefinition(
                sequential = false,
                inputCollection = "authors",
                inputElement = "author",
            )
        )
    }

    @Test
    fun `operaton extract reads io mappings`() {

        // given
        val model = extract(ProcessModelReader(CamundaDialect(OPERATON_NAMESPACE)), "operaton-send-newsletter")

        // then
        assertThat(model.ioMappingOf("serviceTask_loadSubscribers")).isEqualTo(
            IoMapping(
                outputs = listOf(
                    IoMapping.Parameter(target = "subscribers", source = "\${subscribers}"),
                    IoMapping.Parameter(target = "author", source = "\${author}"),
                )
            )
        )
    }

    @Test
    fun `an activity without loop characteristics or io mapping reports neither facet`() {

        // given: the same task in all three dialects, configured with neither facet
        val models = listOf(
            extract(ProcessModelReader(ZeebeDialect()), "c8-send-newsletter"),
            extract(ProcessModelReader(CamundaDialect(CAMUNDA_7_NAMESPACE)), "c7-send-newsletter"),
            extract(ProcessModelReader(CamundaDialect(OPERATON_NAMESPACE)), "operaton-send-newsletter"),
        )

        // then: absent facets stay null instead of collapsing to an empty object
        models.forEach { model ->
            assertThat(model.multiInstanceOf("serviceTask_loadSubscribers")).isNull()
            assertThat(model.ioMappingOf("serviceTask_sendToSubscriber")).isNull()
        }
    }

    @Test
    fun `the same logical loop normalises identically across engines`() {

        // given: the same process modelled for all three engines
        val models = listOf(
            extract(ProcessModelReader(ZeebeDialect()), "c8-send-newsletter"),
            extract(ProcessModelReader(CamundaDialect(CAMUNDA_7_NAMESPACE)), "c7-send-newsletter"),
            extract(ProcessModelReader(CamundaDialect(OPERATON_NAMESPACE)), "operaton-send-newsletter"),
        )

        // then: everything but the engine's own expression syntax agrees
        assertThat(models.map { it.multiInstanceOf("serviceTask_sendToSubscriber")?.sequential })
            .containsOnly(true)
        assertThat(models.map { it.multiInstanceOf("serviceTask_notifyAuthor")?.sequential })
            .containsOnly(false)
        assertThat(models.map { it.multiInstanceOf("serviceTask_sendToSubscriber")?.inputElement })
            .containsOnly("subscriber")
        assertThat(models.map { it.multiInstanceOf("serviceTask_notifyAuthor")?.inputElement })
            .containsOnly("author")
        assertThat(models.map { it.ioMappingOf("serviceTask_loadSubscribers")?.outputs?.map { output -> output.target } })
            .containsOnly(listOf("subscribers", "author"))
    }

    private fun extract(reader: ProcessModelReader, fixture: String): ProcessModel {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/$fixture.bpmn"))
        return reader.read(File(resourceUrl.toURI()).readBytes())
    }

    private fun ProcessModel.activity(id: String): FlowNodeDefinition.Activity {
        return allFlowNodes.single { it.id == id } as FlowNodeDefinition.Activity
    }

    private fun ProcessModel.multiInstanceOf(id: String): MultiInstanceDefinition? = activity(id).multiInstance

    private fun ProcessModel.ioMappingOf(id: String): IoMapping? = activity(id).ioMapping

    private companion object {
        const val CAMUNDA_7_NAMESPACE = "http://camunda.org/schema/1.0/bpmn"
        const val OPERATON_NAMESPACE = "http://operaton.org/schema/1.0/bpmn"
    }
}
