package io.miragon.bpmn.adapter.outbound.codegen

import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraphFactory
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.testBikeLeasingModel
import io.miragon.bpmn.domain.testProcessModel
import io.miragon.bpmn.domain.testProcessModelApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProcessApiGraphTest {

    @Test
    fun `of names the api after the model and carries its flow`() {
        // given: the bike-leasing model for Camunda 7
        val model = testBikeLeasingModel()
        val modelApi = testProcessModelApi(
            model = model,
            packagePath = "de.emaarco.example",
            engine = ProcessEngine.CAMUNDA_7,
        )

        // when: building the process api graph
        val api = ProcessApiGraph.of(modelApi)

        // then: it holds the identity of the process and its flow graph
        assertThat(api).isEqualTo(
            ProcessApiGraph(
                typeName = ApiTypeName("BikeLeasingProcessApi"),
                packagePath = PackagePath("de.emaarco.example"),
                processId = ProcessId("bikeLeasing"),
                processEngine = ProcessEngine.CAMUNDA_7,
                flow = FlowGraphFactory.build(model.graph, model.definitions),
            ),
        )
    }

    @Test
    fun `of leaves out the flow of a model without flow nodes`() {
        val modelApi = testProcessModelApi(model = testProcessModel(flowNodes = emptyList()))

        val api = ProcessApiGraph.of(modelApi)

        assertThat(api.flow).isNull()
    }
}
