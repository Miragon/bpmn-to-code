package io.miragon.bpmn.adapter.outbound.codegen.builder

import io.miragon.bpmn.domain.BpmnModelApi
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.ProcessModel.Variant
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.shared.VariableDefinition
import io.miragon.bpmn.domain.shared.VariableDirection
import io.miragon.bpmn.domain.testProcessModelApi
import io.miragon.bpmn.domain.testSendNewsletterModel
import io.miragon.bpmn.domain.testSubscribeNewsletterModel
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
        // given: a BPMN model with custom service task implementations
        val modelApi = testProcessModelApi(
            packagePath = "de.emaarco.example",
            model = testSubscribeNewsletterModel(
                flowNodes = buildSubscribeNewsletterFlowNodes(
                    confirmationMailImpl = "#{newsletterSendConfirmationMail}",
                    welcomeMailImpl = "\${newsletterSendWelcomeMail}",
                    registrationCompletedImpl = "newsletter.registrationCompleted",
                    notifyCommunityImpl = "newsletter.notifyCommunity",
                    extraVariables = listOf(VariableDefinition("testVariable", VariableDirection.INPUT)),
                ),
            ),
        )

        // when: we build the process API file
        val result = underTest.buildApiFile(modelApi)

        // then: a single model file is returned at the root package
        assertThat(result.fileName).isEqualTo("${modelApi.fileName()}.kt")
        assertThat(result.packagePath).isEqualTo("de.emaarco.example")

        val expectedFile = File(requireNotNull(javaClass.getResource("/api/NewsletterSubscriptionProcessApiKotlin.txt")).toURI())
        assertThat(result.content).isEqualTo(expectedFile.readText())
        assertKotlinSyntaxValid(result.content)

        // and: key KDoc blocks disambiguate the nested objects
        assertThat(result.content).contains("process-level tests")
        assertThat(result.content).contains("@JobWorker(type = ServiceTasks.X)")
        assertThat(result.content).contains("Typed navigation over the process flow")
    }

    @Test
    fun `buildApiFile generates variant-scoped Relations for merged model`() {
        // given: a merged model with a single variant
        val send = testSendNewsletterModel(variantName = "send")
        val merged = ProcessModel(
            processId = send.processId,
            flowNodes = send.flowNodes,
            definitions = send.definitions,
            variants = listOf(
                Variant("send", send.flowNodes, send.sequenceFlows),
            ),
        )
        val modelApi = BpmnModelApi(merged, OutputLanguage.KOTLIN, "de.emaarco.example", ProcessEngine.ZEEBE)

        // when: we build the process API file
        val result = underTest.buildApiFile(modelApi)

        // then: output contains Variants section instead of a flat Relations
        val expectedFile = File(requireNotNull(javaClass.getResource("/api/MultiVariantProcessApiKotlin.txt")).toURI())
        assertThat(result.content).isEqualTo(expectedFile.readText())
        assertKotlinSyntaxValid(result.content)
    }

    @Test
    fun `buildApiFile emits one constant for root elements that share a name`() {
        // given: two bpmn:Message root elements with the same name and their own ids — the domain keeps
        // both so that every messageRef resolves, but they normalise to a single constant
        val model = testSubscribeNewsletterModel(
            messages = listOf(
                RootElementDefinition.Message(id = "Message_1", name = "Message_FormSubmitted"),
                RootElementDefinition.Message(id = "Message_2", name = "Message_FormSubmitted"),
            ),
        )

        // when
        val result = underTest.buildApiFile(testProcessModelApi(model = model))

        // then: a duplicate property would not compile, so the collapsing has to happen before emitting
        assertThat(result.content.split("val MESSAGE_FORM_SUBMITTED").size - 1).isEqualTo(1)
        assertKotlinSyntaxValid(result.content)
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
