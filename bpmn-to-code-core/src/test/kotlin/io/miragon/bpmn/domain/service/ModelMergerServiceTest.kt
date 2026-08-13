package io.miragon.bpmn.domain.service

import io.miragon.bpmn.domain.jobWorkerTask
import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.shared.SequenceFlowDefinition
import io.miragon.bpmn.domain.shared.TimerType
import io.miragon.bpmn.domain.shared.VariableDefinition
import io.miragon.bpmn.domain.shared.VariableDirection
import io.miragon.bpmn.domain.testProcessModel
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ModelMergerServiceTest {

    private val underTest = ModelMergerService()

    @Test
    fun `merges processes with same id into ProcessModel`() {

        // given: two models with same processId and one with different processId
        val firstFlowNode = jobWorkerTask(id = "create-order", jobType = "firstTaskType")
        val secondFlowNode = jobWorkerTask(id = "update-order", jobType = "secondTaskType")
        val thirdFlowNode = jobWorkerTask(id = "delete-order", jobType = "thirdTaskType")
        val firstMessage = RootElementDefinition.Message(id = "firstMessageId", name = "firstMessageName")
        val secondMessage = RootElementDefinition.Message(id = "secondMessageId", name = "secondMessageName")
        val thirdMessage = RootElementDefinition.Message(id = "thirdMessageId", name = "thirdMessageName")
        val firstEscalation = RootElementDefinition.Escalation(id = "ESC_1", name = "firstEscalation", code = "100")
        val secondEscalation = RootElementDefinition.Escalation(id = "ESC_2", name = "secondEscalation", code = "200")
        val thirdEscalation = RootElementDefinition.Escalation(id = "ESC_3", name = "thirdEscalation", code = "300")

        val firstModel = testProcessModel(
            processId = "order-process",
            variantName = "variantA",
            flowNodes = listOf(firstFlowNode, secondFlowNode),
            messages = listOf(firstMessage, secondMessage),
            escalations = listOf(firstEscalation, secondEscalation),
        )
        val secondModel = testProcessModel(
            processId = "order-process",
            variantName = "variantB",
            flowNodes = listOf(secondFlowNode, thirdFlowNode),
            messages = listOf(secondMessage, thirdMessage),
            escalations = listOf(secondEscalation, thirdEscalation),
        )
        val otherModel = testProcessModel(
            processId = "other-order-process",
            flowNodes = listOf(firstFlowNode, secondFlowNode),
            messages = listOf(firstMessage, secondMessage),
            escalations = listOf(firstEscalation),
        )

        // when: merging all models
        val result = underTest.mergeModels(listOf(firstModel, secondModel, otherModel))

        // then: multi-model group produces ProcessModel with deduplicated shared elements
        assertThat(result).hasSize(2)

        val orderProcess = result.first { it.processId == "order-process" }
        assertThat(orderProcess.isMerged).isTrue()
        assertThat(orderProcess.flowNodes).containsExactly(firstFlowNode, thirdFlowNode, secondFlowNode)
        assertThat(orderProcess.definitions.messages).containsExactly(firstMessage, secondMessage, thirdMessage)
        assertThat(orderProcess.definitions.escalations).containsExactly(firstEscalation, secondEscalation, thirdEscalation)
        assertThat(orderProcess.variants).hasSize(2)

        // and: the single-file process carries no variants
        val otherProcess = result.first { it.processId == "other-order-process" }
        assertThat(otherProcess.isMerged).isFalse()
        assertThat(otherProcess.flowNodes).containsExactly(firstFlowNode, secondFlowNode)
        assertThat(otherProcess.definitions.messages).containsExactly(firstMessage, secondMessage)
        assertThat(otherProcess.definitions.escalations).containsExactly(firstEscalation)
    }

    @Test
    fun `sorts all collections alphabetically by raw name`() {

        // given: model with unsorted elements
        val model = testProcessModel(
            processId = "test-process",
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(
                    id = "z-node",
                    variables = listOf(VariableDefinition("alphaVar", VariableDirection.INPUT)),
                ),
                FlowNodeDefinition.Unknown(
                    id = "a-node",
                    variables = listOf(VariableDefinition("zetaVar", VariableDirection.INPUT)),
                ),
                FlowNodeDefinition.Unknown(id = "m-node"),
            ),
            escalations = listOf(
                RootElementDefinition.Escalation(id = "ESC_Z", name = "zEscalation", code = "300"),
                RootElementDefinition.Escalation(id = "ESC_A", name = "aEscalation", code = "100"),
                RootElementDefinition.Escalation(id = "ESC_M", name = "mEscalation", code = "200"),
            ),
        )

        // when: merging a single model
        val result = underTest.mergeModels(listOf(model))

        // then: collections should be sorted independently by their own raw name
        val sortedModel = result.first()
        assertThat(sortedModel.flowNodes.map { it.getRawName() }).containsExactly("a-node", "m-node", "z-node")
        assertThat(sortedModel.variables.map { it.getRawName() }).containsExactly("alphaVar", "zetaVar")
        assertThat(sortedModel.definitions.escalations.map { it.getRawName() }).containsExactly("aEscalation", "mEscalation", "zEscalation")
    }

    @Test
    fun `deduplicates all elements within single BPMN model`() {

        // given: a single model with duplicates of various element types
        val timerFlowNode = FlowNodeDefinition.Event(
            id = "TIMER_1",
            shape = EventShape.INTERMEDIATE_CATCH_EVENT,
            eventDefinitions = listOf(EventDefinitionInstance.Timer(TimerType.DATE, "2024-01-01")),
        )
        val model = testProcessModel(
            processId = "test-process",
            errors = listOf(
                RootElementDefinition.Error(id = "TEST_ERROR", name = "TEST_ERROR", code = "400"),
                RootElementDefinition.Error(id = "TEST_ERROR", name = "TEST_ERROR", code = "400"),
            ),
            signals = listOf(
                RootElementDefinition.Signal(id = "TEST_SIGNAL", name = "TEST_SIGNAL"),
                RootElementDefinition.Signal(id = "TEST_SIGNAL", name = "TEST_SIGNAL"),
            ),
            messages = listOf(
                RootElementDefinition.Message(id = "TEST_MESSAGE", name = "TEST_MESSAGE"),
                RootElementDefinition.Message(id = "TEST_MESSAGE", name = "TEST_MESSAGE"),
            ),
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "node-1"),
                FlowNodeDefinition.Unknown(id = "node-1"),
                timerFlowNode,
                timerFlowNode,
            ),
            escalations = listOf(
                RootElementDefinition.Escalation(id = "TEST_ESC", name = "TEST_ESC", code = "500"),
                RootElementDefinition.Escalation(id = "TEST_ESC", name = "TEST_ESC", code = "500"),
            ),
        )

        // when: merging a single model
        val result = underTest.mergeModels(listOf(model))

        // then: duplicates should be removed from all element types
        val merged = result.first()
        assertThat(merged.definitions.errors).containsExactly(RootElementDefinition.Error(id = "TEST_ERROR", name = "TEST_ERROR", code = "400"))
        assertThat(merged.definitions.signals).containsExactly(RootElementDefinition.Signal(id = "TEST_SIGNAL", name = "TEST_SIGNAL"))
        assertThat(merged.definitions.messages).containsExactly(RootElementDefinition.Message(id = "TEST_MESSAGE", name = "TEST_MESSAGE"))
        assertThat(merged.flowNodes).containsExactly(timerFlowNode, FlowNodeDefinition.Unknown(id = "node-1"))
        assertThat(merged.definitions.escalations).containsExactly(RootElementDefinition.Escalation(id = "TEST_ESC", name = "TEST_ESC", code = "500"))
    }

    @Test
    fun `deduplicates shared elements across multiple BPMN models with same process ID`() {

        // given: two models with overlapping elements
        val firstModel = testProcessModel(
            processId = "test-process",
            variantName = "variantA",
            errors = listOf(
                RootElementDefinition.Error(id = "ERROR_1", name = "ERROR_1", code = "400"),
                RootElementDefinition.Error(id = "ERROR_2", name = "ERROR_2", code = "500"),
            ),
            signals = listOf(
                RootElementDefinition.Signal(id = "SIGNAL_1", name = "SIGNAL_1"),
                RootElementDefinition.Signal(id = "SIGNAL_2", name = "SIGNAL_2"),
            ),
            messages = listOf(
                RootElementDefinition.Message(id = "MSG_1", name = "MSG_1"),
                RootElementDefinition.Message(id = "MSG_2", name = "MSG_2"),
            ),
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "node-1"),
                FlowNodeDefinition.Unknown(id = "node-2"),
            ),
            escalations = listOf(
                RootElementDefinition.Escalation(id = "ESC_1", name = "ESC_1", code = "100"),
                RootElementDefinition.Escalation(id = "ESC_2", name = "ESC_2", code = "200"),
            ),
        )
        val secondModel = testProcessModel(
            processId = "test-process",
            variantName = "variantB",
            errors = listOf(
                RootElementDefinition.Error(id = "ERROR_2", name = "ERROR_2", code = "500"),
                RootElementDefinition.Error(id = "ERROR_3", name = "ERROR_3", code = "600"),
            ),
            signals = listOf(
                RootElementDefinition.Signal(id = "SIGNAL_2", name = "SIGNAL_2"),
                RootElementDefinition.Signal(id = "SIGNAL_3", name = "SIGNAL_3"),
            ),
            messages = listOf(
                RootElementDefinition.Message(id = "MSG_2", name = "MSG_2"),
                RootElementDefinition.Message(id = "MSG_3", name = "MSG_3"),
            ),
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "node-2"),
                FlowNodeDefinition.Unknown(id = "node-3"),
            ),
            escalations = listOf(
                RootElementDefinition.Escalation(id = "ESC_2", name = "ESC_2", code = "200"),
                RootElementDefinition.Escalation(id = "ESC_3", name = "ESC_3", code = "300"),
            ),
        )

        // when: merging models
        val result = underTest.mergeModels(listOf(firstModel, secondModel))

        // then: should produce ProcessModel with deduplicated shared elements
        assertThat(result).hasSize(1)
        val merged = result.first()
        assertThat(merged.isMerged).isTrue()
        assertThat(merged.definitions.errors.map { it.getRawName() }).containsExactly("ERROR_1", "ERROR_2", "ERROR_3")
        assertThat(merged.definitions.signals.map { it.getRawName() }).containsExactly("SIGNAL_1", "SIGNAL_2", "SIGNAL_3")
        assertThat(merged.definitions.messages.map { it.getRawName() }).containsExactly("MSG_1", "MSG_2", "MSG_3")
        assertThat(merged.flowNodes.map { it.getRawName() }).containsExactly("node-1", "node-2", "node-3")
        assertThat(merged.definitions.escalations.map { it.getRawName() }).containsExactly("ESC_1", "ESC_2", "ESC_3")
        assertThat(merged.variants).hasSize(2)
    }

    @Test
    fun `preserves per-variant sequence flows and flow nodes`() {

        // given: two models with the same processId but different flows
        val sharedNode = FlowNodeDefinition.Unknown(id = "Gateway_Route")
        val flowDeOnly = SequenceFlowDefinition("Flow_DE", "Gateway_Route", "Task_DE", conditionExpression = "country=DE")
        val flowAtOnly = SequenceFlowDefinition("Flow_AT", "Gateway_Route", "Task_AT", conditionExpression = "country=AT")
        val deModel = testProcessModel(
            processId = "order-process",
            variantName = "prodDe",
            flowNodes = listOf(sharedNode, FlowNodeDefinition.Unknown(id = "Task_DE", incoming = listOf("Flow_DE"))),
            sequenceFlows = listOf(flowDeOnly),
        )
        val atModel = testProcessModel(
            processId = "order-process",
            variantName = "prodAt",
            flowNodes = listOf(sharedNode, FlowNodeDefinition.Unknown(id = "Task_AT", incoming = listOf("Flow_AT"))),
            sequenceFlows = listOf(flowAtOnly),
        )

        // when: merging models
        val result = underTest.mergeModels(listOf(deModel, atModel))

        // then: result is a ProcessModel with per-variant data
        assertThat(result).hasSize(1)
        val merged = result.first()
        assertThat(merged.isMerged).isTrue()
        val mergedModel = merged

        // and: shared flow nodes are deduplicated
        assertThat(mergedModel.flowNodes.map { it.getRawName() }).containsExactly("Gateway_Route", "Task_AT", "Task_DE")

        // and: each variant preserves its own flows and flow nodes
        val deVariant = mergedModel.variants.first { it.variantName == "prodDe" }
        assertThat(deVariant.sequenceFlows).containsExactly(flowDeOnly)
        assertThat(deVariant.flowNodes.map { it.getRawName() }).containsExactly("Gateway_Route", "Task_DE")

        val atVariant = mergedModel.variants.first { it.variantName == "prodAt" }
        assertThat(atVariant.sequenceFlows).containsExactly(flowAtOnly)
        assertThat(atVariant.flowNodes.map { it.getRawName() }).containsExactly("Gateway_Route", "Task_AT")

        // and: top-level sequenceFlows holds the union across variants, so a consumer ignoring variants
        // still sees the complete process (ADR 018 — was previously returned empty)
        assertThat(mergedModel.sequenceFlows).containsExactlyInAnyOrder(flowDeOnly, flowAtOnly)
    }

    @Test
    fun `unions additionalInputVariables across variants on a shared flow node`() {

        // given: two variants both define the same message start event with different additional input variables
        val variantA = testProcessModel(
            processId = "order-process",
            variantName = "variantA",
            flowNodes = listOf(
                FlowNodeDefinition.Event(
                    id = "MessageStart_1",
                    shape = EventShape.START_EVENT,
                    variables = listOf(
                        VariableDefinition("varA1", VariableDirection.INPUT),
                        VariableDefinition("shared", VariableDirection.INPUT),
                    ),
                ),
            ),
        )
        val variantB = testProcessModel(
            processId = "order-process",
            variantName = "variantB",
            flowNodes = listOf(
                FlowNodeDefinition.Event(
                    id = "MessageStart_1",
                    shape = EventShape.START_EVENT,
                    variables = listOf(
                        VariableDefinition("varB1", VariableDirection.INPUT),
                        VariableDefinition("shared", VariableDirection.INPUT),
                    ),
                ),
            ),
        )

        // when: merging the two variants
        val result = underTest.mergeModels(listOf(variantA, variantB))

        // then: the merged top-level flow node carries the union of all variants' variables, deduplicated
        val merged = result.first()
        val mergedNode = merged.flowNodes.first { it.getRawName() == "MessageStart_1" }
        assertThat(mergedNode.variables).containsExactlyInAnyOrder(
            VariableDefinition("varA1", VariableDirection.INPUT),
            VariableDefinition("varB1", VariableDirection.INPUT),
            VariableDefinition("shared", VariableDirection.INPUT),
        )

        // and: each variant retains its own original variables on its variant-scoped flow nodes
        val variantANode = merged.variants.first { it.variantName == "variantA" }.flowNodes
            .first { it.getRawName() == "MessageStart_1" }
        assertThat(variantANode.variables).containsExactly(
            VariableDefinition("varA1", VariableDirection.INPUT),
            VariableDefinition("shared", VariableDirection.INPUT),
        )
    }

    @Test
    fun `preserves variables on a flow node that exists only in one variant`() {

        // given: a node that exists only in variantB
        val variantA = testProcessModel(
            processId = "order-process",
            variantName = "variantA",
            flowNodes = listOf(FlowNodeDefinition.Unknown(id = "Task_Shared")),
        )
        val variantB = testProcessModel(
            processId = "order-process",
            variantName = "variantB",
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "Task_Shared"),
                FlowNodeDefinition.Unknown(
                    id = "Task_OnlyInB",
                    variables = listOf(VariableDefinition("onlyInB", VariableDirection.OUTPUT)),
                ),
            ),
        )

        // when: merging
        val result = underTest.mergeModels(listOf(variantA, variantB))

        // then: variant-only node and its variables surface in the merged top-level flow nodes
        val merged = result.first()
        val onlyInB = merged.flowNodes.first { it.getRawName() == "Task_OnlyInB" }
        assertThat(onlyInB.variables).containsExactly(VariableDefinition("onlyInB", VariableDirection.OUTPUT))
    }

    @Test
    fun `orders variants and base node selection deterministically regardless of input order`() {

        // given: three variants of one process, each providing a different name for the shared node
        fun variant(name: String) = testProcessModel(
            processId = "order-process",
            variantName = name,
            flowNodes = listOf(FlowNodeDefinition.Unknown(id = "Task_Shared", displayName = "name-from-$name")),
        )
        val dev = variant("dev")
        val prod = variant("prod")
        val staging = variant("staging")

        // when: merging the same set in two different input orders
        val forward = underTest.mergeModels(listOf(dev, prod, staging)).first()
        val shuffled = underTest.mergeModels(listOf(staging, dev, prod)).first()

        // then: variants are emitted sorted by variantName, independent of input order
        assertThat(forward.variants.map { it.variantName }).containsExactly("dev", "prod", "staging")
        assertThat(shuffled.variants.map { it.variantName }).containsExactly("dev", "prod", "staging")

        // and: the merged base node takes its attributes from the first variant by name ("dev")
        assertThat(forward.flowNodes.first { it.getRawName() == "Task_Shared" }.displayName).isEqualTo("name-from-dev")
        assertThat(shuffled.flowNodes.first { it.getRawName() == "Task_Shared" }.displayName).isEqualTo("name-from-dev")
    }

    @Test
    fun `returns a single-file process without variants`() {

        // given: a single model
        val flow = SequenceFlowDefinition("Flow_1", "Start", "End")
        val model = testProcessModel(
            processId = "simple-process",
            flowNodes = listOf(FlowNodeDefinition.Unknown(id = "Start"), FlowNodeDefinition.Unknown(id = "End")),
            sequenceFlows = listOf(flow),
        )

        // when: merging a single model
        val result = underTest.mergeModels(listOf(model))

        // then: no variant wrapping happens for a single file
        assertThat(result).hasSize(1)
        assertThat(result.first().isMerged).isFalse()
        assertThat(result.first().sequenceFlows).containsExactly(flow)
    }

    @Test
    fun `throws when multiple models share processId without variantName`() {

        // given: two models with same processId but no variantName
        val model1 = testProcessModel(processId = "order-process")
        val model2 = testProcessModel(processId = "order-process")

        // when / then
        assertThatThrownBy { underTest.mergeModels(listOf(model1, model2)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("order-process")
            .hasMessageContaining("variantName")
    }

    @Test
    fun `keeps root elements that share a name but have their own id`() {

        // given: a model whose modeller created two bpmn:Message elements with the same name — the common
        // result of typing the same name on two events instead of picking the existing message
        val model = testProcessModel(
            processId = "order-process",
            messages = listOf(
                RootElementDefinition.Message(id = "Message_1", name = "OrderPlaced"),
                RootElementDefinition.Message(id = "Message_2", name = "OrderPlaced"),
            ),
            signals = listOf(
                RootElementDefinition.Signal(id = "Signal_1", name = "OrderCancelled"),
                RootElementDefinition.Signal(id = "Signal_2", name = "OrderCancelled"),
            ),
        )

        // when
        val merged = underTest.mergeModels(listOf(model)).single()

        // then: both survive, so every messageRef emitted by the extractor still resolves in the registry
        assertThat(merged.definitions.messages.map { it.id }).containsExactlyInAnyOrder("Message_1", "Message_2")
        assertThat(merged.definitions.signals.map { it.id }).containsExactlyInAnyOrder("Signal_1", "Signal_2")
    }

    @Test
    fun `reports a merged process as non-executable when no variant is executable`() {

        // given: two variants of one process, both marked isExecutable="false"
        val first = testProcessModel(processId = "order-process", variantName = "de").copy(isExecutable = false)
        val second = testProcessModel(processId = "order-process", variantName = "en").copy(isExecutable = false)

        // when
        val merged = underTest.mergeModels(listOf(first, second)).single()

        // then: the merged model must not claim to be executable — the JSON publishes this flag
        assertThat(merged.isExecutable).isFalse()
    }

    @Test
    fun `reports a merged process as executable when at least one variant is`() {

        // given
        val first = testProcessModel(processId = "order-process", variantName = "de").copy(isExecutable = false)
        val second = testProcessModel(processId = "order-process", variantName = "en")

        // when
        val merged = underTest.mergeModels(listOf(first, second)).single()

        // then
        assertThat(merged.isExecutable).isTrue()
    }

    @Test
    fun `deduplicates root elements that repeat across variants`() {

        // given: two variants that both reference the same root elements
        val shared = RootElementDefinition.Message(id = "Message_1", name = "OrderPlaced")
        val first = testProcessModel(processId = "order-process", variantName = "de", messages = listOf(shared))
        val second = testProcessModel(processId = "order-process", variantName = "en", messages = listOf(shared))

        // when
        val merged = underTest.mergeModels(listOf(first, second)).single()

        // then: the same id appears once, not once per variant
        assertThat(merged.definitions.messages.map { it.id }).containsExactly("Message_1")
    }

    @Test
    fun `throws when some models have variantName and some do not`() {

        // given: mixed variantName presence
        val model1 = testProcessModel(processId = "order-process", variantName = "prodDe")
        val model2 = testProcessModel(processId = "order-process")

        // when / then
        assertThatThrownBy { underTest.mergeModels(listOf(model1, model2)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("variantName")
    }
}
