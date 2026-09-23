package io.miragon.bpmn.adapter.outbound.codegen.builder

import io.miragon.bpmn.domain.BpmnModelApi
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.ProcessModel.Variant
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.shared.VariableDefinition
import io.miragon.bpmn.domain.shared.VariableDirection
import io.miragon.bpmn.domain.testProcessModelApi
import io.miragon.bpmn.domain.testSendNewsletterModel
import io.miragon.bpmn.domain.testSubscribeNewsletterModel
import io.miragon.bpmn.domain.withId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class CSharpProcessApiBuilderTest {

    private val underTest = CSharpProcessApiBuilder()

    @Test
    fun `buildApiFile generates correct process API file`() {
        // given: a BPMN model with custom service task implementations
        val modelApi = testProcessModelApi(
            packagePath = "de.emaarco.example",
            language = OutputLanguage.CSHARP,
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
        assertThat(result.fileName).isEqualTo("${modelApi.fileName()}.cs")
        assertThat(result.packagePath).isEqualTo("de.emaarco.example")
        assertThat(result.language).isEqualTo(OutputLanguage.CSHARP)

        val expectedFile = File(requireNotNull(javaClass.getResource("/api/NewsletterSubscriptionProcessApiCsharp.txt")).toURI())
        assertThat(result.content).isEqualTo(expectedFile.readText())
    }

    @Test
    fun `buildApiFile omits the navigation sections that need the jvm runtime`() {
        // given: a merged model, whose navigation the jvm builders emit as a Variants section
        val send = testSendNewsletterModel(variantName = "send")
        val merged = ProcessModel(
            processId = send.processId,
            flowNodes = send.flowNodes,
            definitions = send.definitions,
            variants = listOf(Variant("send", send.flowNodes, send.sequenceFlows)),
        )
        val modelApi = BpmnModelApi(merged, OutputLanguage.CSHARP, "de.emaarco.example", ProcessEngine.ZEEBE)

        // when: we build the process API file
        val result = underTest.buildApiFile(modelApi)

        // then: neither navigation section is generated, but the constants are
        assertThat(result.content).doesNotContain("class Variants", "class Flow")
        assertThat(result.content).contains("public static class Elements")
    }

    @Test
    fun `buildApiFile omits relations for an unmerged model`() {
        // given: a plain, unmerged model
        val modelApi = testProcessModelApi(
            packagePath = "de.emaarco.example",
            language = OutputLanguage.CSHARP,
            model = testSubscribeNewsletterModel(),
        )

        // when: we build the process API file
        val result = underTest.buildApiFile(modelApi)

        // then: the navigation DSL is absent
        assertThat(result.content).doesNotContain("class Flow")
    }

    @Test
    fun `renames a member that would collide with its enclosing type`() {
        // given: a model whose first flow node is named exactly like the section that will contain it
        val defaultModel = testSubscribeNewsletterModel()
        val collidingNodes = defaultModel.flowNodes.mapIndexed { index, node ->
            if (index == 0) node.withId("Elements") else node
        }
        val modelApi = testProcessModelApi(
            model = testSubscribeNewsletterModel(flowNodes = collidingNodes),
            packagePath = "de.emaarco.example",
            language = OutputLanguage.CSHARP,
        )

        // when: we build the process API file
        val result = underTest.buildApiFile(modelApi)

        // then: the constant is renamed, because C# rejects a member named like its enclosing type (CS0542)
        assertThat(result.content).contains("public const string Elements_ = \"Elements\";")
        assertThat(result.content).doesNotContain("public const string Elements =")
    }

    @Test
    fun `maps content of id to valid identifier format`() {
        // given: a model with flow nodes whose ids use dashes
        val defaultModel = testSubscribeNewsletterModel()
        val modifiedNodes = defaultModel.flowNodes.map { it.withId(it.getName().replace("_", "-")) }
        val modelApi = testProcessModelApi(
            model = testSubscribeNewsletterModel(flowNodes = modifiedNodes),
            packagePath = "de.emaarco.example",
            language = OutputLanguage.CSHARP,
        )

        // when: we build the process API file
        val result = underTest.buildApiFile(modelApi)

        // then: dashes never leak into an identifier, only into the string values
        val declarations = result.content.lines().filter { it.contains("public const string") || it.contains("public static class") }
        assertThat(declarations).isNotEmpty()
        assertThat(declarations.map { it.substringBefore("=") }).noneMatch { it.contains("-") }
    }
}
