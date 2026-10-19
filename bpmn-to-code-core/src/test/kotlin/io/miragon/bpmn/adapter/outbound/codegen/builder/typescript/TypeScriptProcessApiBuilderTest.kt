package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

import io.miragon.bpmn.adapter.outbound.assertMatchesGolden
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.MessageReference
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.testBikeLeasingModel
import io.miragon.bpmn.domain.testCancelBikeOrderModel
import io.miragon.bpmn.domain.testProcessModel
import io.miragon.bpmn.domain.testProcessModelApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TypeScriptProcessApiBuilderTest {

    private val underTest = TypeScriptProcessApiBuilder()

    @Test
    fun `buildApiFile generates correct process API file`() {
        // given: the bike-leasing model, which covers every implementation kind of Camunda 7
        val modelApi = typeScriptApi(testBikeLeasingModel())

        // when: we build the process API file
        val result = underTest.buildApiFile(modelApi)

        // then: a single TypeScript file is returned in the requested package
        assertThat(result.fileName).isEqualTo("${modelApi.fileName()}.ts")
        assertThat(result.packagePath).isEqualTo("de.emaarco.example")
        assertThat(result.language).isEqualTo(OutputLanguage.TYPESCRIPT)
        assertMatchesGolden(result.content, "/api/BikeLeasingProcessApiTypescript.txt")
    }

    @Test
    fun `buildApiFile leads the API name with the variant name of the model`() {
        val result = underTest.buildApiFile(typeScriptApi(testCancelBikeOrderModel(variantName = "retail")))

        assertThat(result.fileName).isEqualTo("RetailCancelBikeOrderProcessApi.ts")
        assertMatchesGolden(result.content, "/api/PrefixedCancelBikeOrderProcessApiTypescript.txt")
    }

    @Test
    fun `imports the runtime and exactly the shared definition files the API refers to`() {
        // given: a process whose only reference is a message
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Event(
                    id = "startEvent_orderReceived",
                    shape = EventShape.START_EVENT,
                    eventDefinitions = listOf(EventDefinitionInstance.Message(MessageReference(messageRef = "message_orderReceived"))),
                ),
            ),
            messages = listOf(
                io.miragon.bpmn.domain.shared.RootElementDefinition.Message(
                    id = "message_orderReceived",
                    name = "miravelo.orderReceived",
                ),
            ),
        )

        // when: we build the process API file
        val result = underTest.buildApiFile(typeScriptApi(model))

        // then: the runtime package and the messages file are imported, the file with an extension that works under nodenext and bundler resolution
        val importLines = result.content.lines().filter { it.startsWith("import ") }
        assertThat(importLines).containsExactly(
            "import type * as Runtime from \"@miragon/bpmn-to-code-runtime\";",
            "import { Messages } from \"./Messages.js\";",
        )
        assertThat(result.content).contains("  readonly message = Messages.MIRAVELO_ORDER_RECEIVED;")
    }

    @Test
    fun `node reaches its successors through getters, named after the elements they lead to`() {
        val result = underTest.buildApiFile(typeScriptApi(testBikeLeasingModel()))

        assertThat(result.content).contains(
            """
            |  readonly next = {
            |    get gatewayCollectRejections(): Runtime.SequenceFlows<GatewayCollectRejections> {
            |      return Runtime.sequenceFlows(GatewayCollectRejections.INSTANCE, { id: "flow_isSolventToCollectRejections", name: "No", conditionExpression: "${'$'}{!solvent}" });
            |    },
            |    get subProcessConcludeContract(): Runtime.SequenceFlows<SubProcessConcludeContract> {
            |      return Runtime.sequenceFlows(SubProcessConcludeContract.INSTANCE, { id: "flow_isSolventToConcludeContract", name: "Yes", isDefault: true });
            |    },
            |  };
            """.trimMargin(),
        )
    }

    @Test
    fun `boundary event reaches its host, and a subprocess the start elements of its interior`() {
        val result = underTest.buildApiFile(typeScriptApi(testBikeLeasingModel()))

        assertThat(result.content).contains("export class TimerSignatureReminder implements Runtime.BoundaryEvent<SubProcessConcludeContract>")
        assertThat(result.content).contains(
            """
            |  get attachedTo(): SubProcessConcludeContract {
            |    return SubProcessConcludeContract.INSTANCE;
            |  }
            """.trimMargin(),
        )
        assertThat(result.content).contains(
            """
            |  readonly startEvents = {
            |    get startEventCustomerEligible(): StartEventCustomerEligible {
            |      return StartEventCustomerEligible.INSTANCE;
            |    },
            |  };
            """.trimMargin(),
        )
    }

    @Test
    fun `FlowNodes holds every node instance and lists them under all`() {
        val result = underTest.buildApiFile(typeScriptApi(testBikeLeasingModel()))

        assertThat(result.content).contains("    GatewayIsSolvent: GatewayIsSolvent.INSTANCE,")
        assertThat(result.content).contains("    get all(): readonly Runtime.FlowNode[] {")
        assertThat(result.content).contains("        GatewayIsSolvent.INSTANCE,\n")
    }

    @Test
    fun `file header marks the file as generated`() {
        val result = underTest.buildApiFile(typeScriptApi(testBikeLeasingModel()))

        assertThat(result.content.lines().first()).isEqualTo("// Generated by bpmn-to-code")
    }

    @Test
    fun `a process without elements imports nothing`() {
        val result = underTest.buildApiFile(typeScriptApi(testProcessModel(flowNodes = emptyList())))

        assertThat(result.content.lines().filter { it.startsWith("import ") }).isEmpty()
        assertThat(result.content).doesNotContain("FlowNodes")
    }

    private fun typeScriptApi(model: ProcessModel) = testProcessModelApi(
        packagePath = "de.emaarco.example",
        language = OutputLanguage.TYPESCRIPT,
        model = model,
    )
}
