package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

import io.miragon.bpmn.adapter.outbound.assertMatchesGolden
import io.miragon.bpmn.adapter.outbound.codegen.CodeGenerationAdapter
import io.miragon.bpmn.domain.ProcessApiNamingException
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.MessageReference
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.testBikeLeasingModel
import io.miragon.bpmn.domain.testCancelBikeOrderModel
import io.miragon.bpmn.domain.testProcessApiRequest
import io.miragon.bpmn.domain.testProcessModel
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class TypeScriptProcessApiBuilderTest {

    private val underTest = CodeGenerationAdapter()

    @Test
    fun `generateCode generates correct process API file`() {
        // given: the bike-leasing model, which covers every implementation kind of Camunda 7
        val request = typeScriptApi(testBikeLeasingModel())

        // when: we build the process API file
        val result = underTest.generateCode(request).single()

        // then: a single TypeScript file is returned in the requested package
        assertThat(result.fileName).isEqualTo("BikeLeasingProcessApi.ts")
        assertThat(result.packagePath).isEqualTo("de.emaarco.example")
        assertThat(result.language).isEqualTo(OutputLanguage.TYPESCRIPT)
        assertMatchesGolden(result.content, "/api/BikeLeasingProcessApiTypescript.txt")
    }

    @Test
    fun `generateCode leads the API name with the variant name of the model`() {
        // given: a model that declares a variant name
        val request = typeScriptApi(testCancelBikeOrderModel(variantName = "retail"))

        // when: we build the process API file
        val result = underTest.generateCode(request).single()

        // then: the API carries the prefix
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
                    eventDefinitions = listOf(
                        EventDefinitionInstance.Message(MessageReference(messageRef = "message_orderReceived")),
                    ),
                ),
            ),
            messages = listOf(
                RootElementDefinition.Message(id = "message_orderReceived", name = "miravelo.orderReceived"),
            ),
        )

        // when: we build the process API file
        val result = underTest.generateCode(typeScriptApi(model)).single()

        // then: the runtime types only and the messages file are imported,
        // with an extension for nodenext and bundler resolution
        val importLines = result.content.lines().filter { it.startsWith("import ") }
        assertThat(importLines).containsExactly(
            "import type * as Runtime from \"@miragon/bpmn-to-code-runtime\";",
            "import { Messages } from \"./Messages.js\";",
        )
        assertThat(result.content).contains("  readonly message = Messages.MIRAVELO_ORDER_RECEIVED;")
    }

    @Test
    fun `node reaches its successors through getters, named after the elements they lead to`() {
        val result = underTest.generateCode(typeScriptApi(testBikeLeasingModel())).single()

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
        val result = underTest.generateCode(typeScriptApi(testBikeLeasingModel())).single()

        assertThat(result.content)
            .contains("export class TimerSignatureReminder implements Runtime.BoundaryEvent<SubProcessConcludeContract>")
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
        val result = underTest.generateCode(typeScriptApi(testBikeLeasingModel())).single()

        assertThat(result.content).contains("    GatewayIsSolvent: GatewayIsSolvent.INSTANCE,")
        assertThat(result.content).contains("    get all(): readonly Runtime.FlowNode[] {")
        assertThat(result.content).contains("        GatewayIsSolvent.INSTANCE,\n")
    }

    @Test
    fun `file header marks the file as generated`() {
        val result = underTest.generateCode(typeScriptApi(testBikeLeasingModel())).single()

        assertThat(result.content.lines().first()).isEqualTo("// Generated by bpmn-to-code")
    }

    @Test
    fun `generation fails for an element named like a module-level import`() {
        // given: a task whose class would shadow the imported messages definitions
        val model = testProcessModel(
            flowNodes = listOf(FlowNodeDefinition.Event(id = "messages", shape = EventShape.START_EVENT)),
        )

        // when / then: generation fails instead of emitting a module tsc rejects
        assertThatThrownBy { underTest.generateCode(typeScriptApi(model)) }
            .isInstanceOf(ProcessApiNamingException::class.java)
            .hasMessageContaining("Messages")
    }

    @Test
    fun `a process without elements imports nothing`() {
        val result = underTest.generateCode(typeScriptApi(testProcessModel(flowNodes = emptyList()))).single()

        assertThat(result.content.lines().filter { it.startsWith("import ") }).isEmpty()
        assertThat(result.content).doesNotContain("FlowNodes")
    }

    private fun typeScriptApi(model: ProcessModel) = testProcessApiRequest(
        packagePath = "de.emaarco.example",
        language = OutputLanguage.TYPESCRIPT,
        model = model,
    )
}
