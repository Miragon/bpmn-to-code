package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin

import io.miragon.bpmn.adapter.outbound.assertMatchesGolden
import io.miragon.bpmn.domain.BpmnModelApi
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.ProcessModel.Variant
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.GatewayKind
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.shared.SequenceFlowDefinition
import io.miragon.bpmn.domain.shared.VariableDefinition
import io.miragon.bpmn.domain.shared.VariableDirection
import io.miragon.bpmn.domain.testBikeLeasingModel
import io.miragon.bpmn.domain.testCancelBikeOrderModel
import io.miragon.bpmn.domain.testProcessModel
import io.miragon.bpmn.domain.testProcessModelApi
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.create
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.junit.jupiter.api.Test

class KotlinProcessApiBuilderTest {

    private val underTest = KotlinProcessApiBuilder()

    @Test
    fun `buildApiFile generates correct process API file`() {
        // given: the bike-leasing model, which covers every implementation kind of Camunda 7
        val modelApi = testProcessModelApi(packagePath = "de.emaarco.example", model = testBikeLeasingModel())

        // when: we build the process API file
        val result = underTest.buildApiFile(modelApi)

        // then: a single model file is returned at the root package
        assertThat(result.fileName).isEqualTo("${modelApi.fileName()}.kt")
        assertThat(result.packagePath).isEqualTo("de.emaarco.example")

        assertMatchesGolden(result.content, "/api/BikeLeasingProcessApiKotlin.txt")
        assertKotlinSyntaxValid(result.content)

        // and: the FlowNodes KDoc explains how to navigate it
        assertThat(result.content).contains("Typed navigation over the process flow")
    }

    @Test
    fun `several flows to the same element share one transition`() {
        // given: a gateway with two conditional flows that both lead to the same task
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Gateway(id = "split", kind = GatewayKind.EXCLUSIVE, outgoing = listOf("flow_small", "flow_vip")),
                FlowNodeDefinition.Unknown(id = "approve", incoming = listOf("flow_small", "flow_vip")),
            ),
            sequenceFlows = listOf(
                SequenceFlowDefinition(
                    id = "flow_small",
                    sourceRef = "split",
                    targetRef = "approve",
                    conditionExpression = "=amount < 100",
                ),
                SequenceFlowDefinition(
                    id = "flow_vip",
                    sourceRef = "split",
                    targetRef = "approve",
                    conditionExpression = "=customer.isVip",
                ),
            ),
        )

        // when
        val result = underTest.buildApiFile(testProcessModelApi(model = model))

        // then: the target is the gateway's one successor, and one SequenceFlows carries both flows
        assertThat(result.content).contains("), LeadsTo<Approve> {")
        assertThat(result.content).containsOnlyOnce("SequenceFlows(")
        assertThat(result.content).contains("\"flow_small\"", "\"flow_vip\"")
        assertKotlinSyntaxValid(result.content)
    }

    @Test
    fun `strips the public modifier without touching string literals`() {
        // given: an element whose display name contains the word "public"
        val model = testProcessModel(
            flowNodes = listOf(FlowNodeDefinition.Unknown(id = "notifyChannel", displayName = "Notify public channel")),
        )

        // when
        val result = underTest.buildApiFile(testProcessModelApi(model = model))

        // then
        assertThat(result.content).contains("name = \"Notify public channel\"")
        assertThat(result.content).doesNotContainPattern("(?m)^\\s*public ")
    }

    @Test
    fun `buildApiFile generates variant-scoped FlowNodes for merged model`() {
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
        val modelApi = BpmnModelApi(
            model = merged,
            outputLanguage = OutputLanguage.KOTLIN,
            packagePath = "de.emaarco.example",
            targetEngine = ProcessEngine.ZEEBE,
        )

        // when: we build the process API file
        val result = underTest.buildApiFile(modelApi)

        // then: output contains FlowVariants section instead of a flat FlowNodes
        assertMatchesGolden(result.content, "/api/MultiVariantProcessApiKotlin.txt")
        assertKotlinSyntaxValid(result.content)
    }

    companion object {

        @OptIn(K1Deprecation::class)
        private val kotlinEnvironment by lazy {
            val config = CompilerConfiguration.create(messageCollector = MessageCollector.NONE)
            KotlinCoreEnvironment.createForProduction(
                projectDisposable = Disposer.newDisposable(),
                configuration = config,
                configFiles = EnvironmentConfigFiles.JVM_CONFIG_FILES,
            )
        }

        @OptIn(K1Deprecation::class)
        private fun assertKotlinSyntaxValid(source: String) {
            val file = KtPsiFactory(kotlinEnvironment.project).createFile(source)
            val errors = mutableListOf<String>()
            file.accept(object : KtTreeVisitorVoid() {
                override fun visitErrorElement(element: PsiErrorElement) {
                    errors.add(element.errorDescription)
                }
            })
            assertThat(errors).withFailMessage { "Kotlin syntax errors in generated output: $errors" }.isEmpty()
        }
    }
}
