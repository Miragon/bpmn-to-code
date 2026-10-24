package io.miragon.bpmn.adapter.outbound.codegen

import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraphFactory
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.testBikeLeasingModel
import io.miragon.bpmn.domain.testProcessApiRequest
import io.miragon.bpmn.domain.testProcessModel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProcessApiGraphTest {

    @Test
    fun `of names the api after the model and carries its flow`() {
        // given: the bike-leasing model for Camunda 7
        val model = testBikeLeasingModel()
        val request = testProcessApiRequest(
            model = model,
            packagePath = "de.emaarco.example",
            engine = ProcessEngine.CAMUNDA_7,
        )

        // when: building the process api graph
        val api = ProcessApiGraph.of(model, request)

        // then: it holds the identity of the process and its flow graph
        assertThat(api).isEqualTo(
            ProcessApiGraph(
                typeName = "BikeLeasingProcessApi",
                packagePath = PackagePath("de.emaarco.example"),
                processId = "bikeLeasing",
                processEngine = ProcessEngine.CAMUNDA_7,
                flow = FlowGraphFactory.build(model.graph, model.definitions),
            ),
        )
    }

    @Test
    fun `of leaves out the flow of a model without flow nodes`() {
        val model = testProcessModel(flowNodes = emptyList())

        val api = ProcessApiGraph.of(model, testProcessApiRequest(model = model))

        assertThat(api.flow).isNull()
    }
}
