package io.miragon.bpmn.adapter.outbound.engine

import io.miragon.bpmn.adapter.outbound.engine.dialect.CamundaDialect
import io.miragon.bpmn.adapter.outbound.engine.dialect.ZeebeDialect
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.IoMapping
import io.miragon.bpmn.domain.shared.MultiInstanceDefinition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Guards the two activity facets the v2 model introduced: multi-instance loop characteristics
 * ([#73](https://github.com/Miragon/bpmn-to-code/issues/73)) and I/O mappings
 * ([#74](https://github.com/Miragon/bpmn-to-code/issues/74)).
 *
 * Each engine spells them differently — `zeebe:loopCharacteristics` / `zeebe:ioMapping` versus
 * `camunda:collection` / `camunda:inputOutput` — but they normalise onto the same domain shape. Only the
 * expressions themselves stay engine-specific, because they are preserved verbatim (FEEL `=bikeIds`,
 * JUEL `${'$'}{bikeIds}`).
 */
class ActivityFacetExtractionTest {

    @Test
    fun `zeebe extract reads multi-instance loop characteristics`() {
        // given
        val model = extract(ProcessModelReader(ZeebeDialect()), "zeebe/bike-leasing")

        // then: isSequential comes from BPMN, the collection bindings from zeebe:loopCharacteristics
        assertThat(model.multiInstanceOf("serviceTask_orderBike")).isEqualTo(
            MultiInstanceDefinition(
                sequential = false,
                inputCollection = "=bikeIds",
                inputElement = "bikeId",
                outputCollection = "orderIds",
                outputElement = "=orderId",
            ),
        )
        assertThat(model.multiInstanceOf("serviceTask_issueInsurancePolicy")).isEqualTo(
            MultiInstanceDefinition(sequential = true, inputCollection = "=bikeIds", inputElement = "bikeId"),
        )
    }

    @Test
    fun `zeebe extract reads io mappings`() {
        // given
        val model = extract(ProcessModelReader(ZeebeDialect()), "zeebe/bike-leasing")

        // then: zeebe:input and zeebe:output keep source and target verbatim
        assertThat(model.ioMappingOf("serviceTask_sendContract")).isEqualTo(
            IoMapping(
                inputs = listOf(IoMapping.Parameter(target = "applicationId", source = "=applicationId")),
                outputs = listOf(IoMapping.Parameter(target = "contractId", source = "=contractId")),
            ),
        )
        assertThat(model.ioMappingOf("serviceTask_orderBike")).isEqualTo(
            IoMapping(
                inputs = listOf(
                    IoMapping.Parameter(target = "authentication.type", source = "noAuth"),
                    IoMapping.Parameter(target = "method", source = "POST"),
                    IoMapping.Parameter(target = "url", source = "https://supplier.miravelo.example/orders"),
                    IoMapping.Parameter(target = "body", source = "={bikeId: bikeId}"),
                ),
                outputs = listOf(IoMapping.Parameter(target = "orderId", source = "=response.body.orderId")),
            ),
        )
    }

    @Test
    fun `camunda 7 extract reads multi-instance loop characteristics`() {
        // given
        val model = extract(ProcessModelReader(CamundaDialect(CAMUNDA_7_NAMESPACE)), "c7/bike-leasing")

        // then: camunda:collection and camunda:elementVariable normalise onto the same fields as Zeebe
        assertThat(model.multiInstanceOf("serviceTask_orderBike")).isEqualTo(
            MultiInstanceDefinition(sequential = false, inputCollection = $$"${bikeIds}", inputElement = "bikeId"),
        )
        assertThat(model.multiInstanceOf("serviceTask_issueInsurancePolicy")).isEqualTo(
            MultiInstanceDefinition(sequential = true, inputCollection = $$"${bikeIds}", inputElement = "bikeId"),
        )
    }

    @Test
    fun `camunda 7 extract reads io mappings`() {
        // given
        val model = extract(ProcessModelReader(CamundaDialect(CAMUNDA_7_NAMESPACE)), "c7/bike-leasing")

        // then: the parameter name becomes the target, the element body the source
        assertThat(model.ioMappingOf("serviceTask_sendContract")).isEqualTo(
            IoMapping(
                inputs = listOf(IoMapping.Parameter(target = "applicationId", source = $$"${applicationId}")),
                outputs = listOf(IoMapping.Parameter(target = "contractId", source = $$"${contractId}")),
            ),
        )
    }

    @Test
    fun `operaton extract reads multi-instance loop characteristics`() {
        // given
        val model = extract(ProcessModelReader(CamundaDialect(OPERATON_NAMESPACE)), "operaton/bike-leasing")

        // then: the operaton namespace carries the identical vocabulary (ADR 010)
        assertThat(model.multiInstanceOf("serviceTask_orderBike")).isEqualTo(
            MultiInstanceDefinition(sequential = false, inputCollection = $$"${bikeIds}", inputElement = "bikeId"),
        )
        assertThat(model.multiInstanceOf("serviceTask_issueInsurancePolicy")).isEqualTo(
            MultiInstanceDefinition(sequential = true, inputCollection = $$"${bikeIds}", inputElement = "bikeId"),
        )
    }

    @Test
    fun `operaton extract reads io mappings`() {
        // given
        val model = extract(ProcessModelReader(CamundaDialect(OPERATON_NAMESPACE)), "operaton/bike-leasing")

        // then
        assertThat(model.ioMappingOf("serviceTask_sendContract")).isEqualTo(
            IoMapping(
                inputs = listOf(IoMapping.Parameter(target = "applicationId", source = $$"${applicationId}")),
                outputs = listOf(IoMapping.Parameter(target = "contractId", source = $$"${contractId}")),
            ),
        )
    }

    @Test
    fun `an activity without loop characteristics or io mapping reports neither facet`() {
        // given: the same process in all three dialects
        val models = bikeLeasingPerEngine()

        // then: absent facets stay null instead of collapsing to an empty object
        models.forEach { model ->
            assertThat(model.multiInstanceOf("serviceTask_sendContract")).isNull()
            assertThat(model.ioMappingOf("serviceTask_issueInsurancePolicy")).isNull()
        }
    }

    @Test
    fun `the same logical loop normalises identically across engines`() {
        // given: the same process modelled for all three engines
        val models = bikeLeasingPerEngine()

        // then: everything but the engine's own expression syntax agrees
        assertThat(models.map { it.multiInstanceOf("serviceTask_orderBike")?.sequential }).containsOnly(false)
        assertThat(models.map { it.multiInstanceOf("serviceTask_issueInsurancePolicy")?.sequential }).containsOnly(true)
        assertThat(models.map { it.multiInstanceOf("serviceTask_orderBike")?.inputElement }).containsOnly("bikeId")
        assertThat(models.map { it.ioMappingOf("serviceTask_sendContract")?.outputs?.map { output -> output.target } })
            .containsOnly(listOf("contractId"))
    }

    private fun bikeLeasingPerEngine(): List<ProcessModel> = listOf(
        extract(ProcessModelReader(ZeebeDialect()), "zeebe/bike-leasing"),
        extract(ProcessModelReader(CamundaDialect(CAMUNDA_7_NAMESPACE)), "c7/bike-leasing"),
        extract(ProcessModelReader(CamundaDialect(OPERATON_NAMESPACE)), "operaton/bike-leasing"),
    )

    private fun extract(reader: ProcessModelReader, fixture: String): ProcessModel {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/$fixture.bpmn"))
        return reader.read(File(resourceUrl.toURI()).readBytes())
    }

    private fun ProcessModel.activity(id: String): FlowNodeDefinition.Activity = allFlowNodes.single { it.id == id } as FlowNodeDefinition.Activity

    private fun ProcessModel.multiInstanceOf(id: String): MultiInstanceDefinition? = activity(id).multiInstance

    private fun ProcessModel.ioMappingOf(id: String): IoMapping? = activity(id).ioMapping

    private companion object {
        const val CAMUNDA_7_NAMESPACE = "http://camunda.org/schema/1.0/bpmn"
        const val OPERATON_NAMESPACE = "http://operaton.org/schema/1.0/bpmn"
    }
}
