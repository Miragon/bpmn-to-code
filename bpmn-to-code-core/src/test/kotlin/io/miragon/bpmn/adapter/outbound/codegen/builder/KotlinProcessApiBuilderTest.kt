package io.miragon.bpmn.adapter.outbound.codegen.builder

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
import java.io.File

class KotlinProcessApiBuilderTest {

    private val underTest = KotlinProcessApiBuilder()

    @Test
    fun `buildApiFile generates correct process API file`() {
        // given: the bike-leasing model, which covers every implementation kind of Camunda 7
        val modelApi = testProcessModelApi(
            packagePath = "de.emaarco.example",
            model = testBikeLeasingModel(),
        )

        // when: we build the process API file
        val result = underTest.buildApiFile(modelApi)

        // then: a single model file is returned at the root package
        assertThat(result.fileName).isEqualTo("${modelApi.fileName()}.kt")
        assertThat(result.packagePath).isEqualTo("de.emaarco.example")

        assertThat(result.content).isEqualTo(golden("/api/BikeLeasingProcessApiKotlin.txt", result.content))
        assertKotlinSyntaxValid(result.content)

        // and: the Flow KDoc explains how to navigate it
        assertThat(result.content).contains("Typed navigation over the process flow")
    }

    @Test
    fun `several flows to the same element share one outgoing-flows property as a list`() {
        // given: a gateway with two conditional flows that both lead to the same task
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Gateway(id = "split", kind = GatewayKind.EXCLUSIVE, outgoing = listOf("flow_small", "flow_vip")),
                FlowNodeDefinition.Unknown(id = "approve", incoming = listOf("flow_small", "flow_vip")),
            ),
            sequenceFlows = listOf(
                SequenceFlowDefinition("flow_small", "split", "approve", conditionExpression = "=amount < 100"),
                SequenceFlowDefinition("flow_vip", "split", "approve", conditionExpression = "=customer.isVip"),
            ),
        )

        // when
        val result = underTest.buildApiFile(testProcessModelApi(model = model))

        // then: one stable name for both flows, typed as a list
        assertThat(result.content).contains("val toApprove: List<SequenceFlow<Approve>>")
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
    fun `buildApiFile generates variant-scoped Flow for merged model`() {
        // given: a merged model with a single variant
        val retail = testCancelBikeOrderModel(variantName = "retail")
        val merged = ProcessModel(
            processId = retail.processId,
            flowNodes = retail.flowNodes,
            definitions = retail.definitions,
            variants = listOf(
                Variant("retail", retail.flowNodes, retail.sequenceFlows),
            ),
        )
        val modelApi = BpmnModelApi(merged, OutputLanguage.KOTLIN, "de.emaarco.example", ProcessEngine.ZEEBE)

        // when: we build the process API file
        val result = underTest.buildApiFile(modelApi)

        // then: output contains FlowVariants section instead of a flat Flow
        assertThat(result.content).isEqualTo(golden("/api/MultiVariantProcessApiKotlin.txt", result.content))
        assertKotlinSyntaxValid(result.content)
    }

    private fun golden(path: String, generated: String): String {
        if (System.getProperty("golden.update") == "true") {
            File("src/test/resources$path").writeText(generated)
            return generated
        }
        return File(requireNotNull(javaClass.getResource(path)).toURI()).readText()
    }

    companion object {

        @OptIn(K1Deprecation::class)
        private val kotlinEnvironment by lazy {
            val config = CompilerConfiguration.create(messageCollector = MessageCollector.NONE)
            KotlinCoreEnvironment.createForProduction(Disposer.newDisposable(), config, EnvironmentConfigFiles.JVM_CONFIG_FILES)
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
            assertThat(errors)
                .withFailMessage { "Kotlin syntax errors in generated output: $errors" }
                .isEmpty()
        }
    }
}
