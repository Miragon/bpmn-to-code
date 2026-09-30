package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import com.sun.source.util.JavacTask
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
import io.miragon.bpmn.domain.withId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.ToolProvider

class JavaProcessApiBuilderTest {

    private val underTest = JavaProcessApiBuilder()

    @Test
    fun `buildApiFile generates correct process API file`() {
        // given: the bike-leasing model, which covers every implementation kind of Camunda 7
        val modelApi = testProcessModelApi(packagePath = "de.emaarco.example", model = testBikeLeasingModel())

        // when: we build the process API file
        val result = underTest.buildApiFile(modelApi)

        // then: a single model file is returned at the root package
        assertThat(result.fileName).isEqualTo("${modelApi.fileName()}.java")
        assertThat(result.packagePath).isEqualTo("de.emaarco.example")

        assertMatchesGolden(result.content, "/api/BikeLeasingProcessApiJava.txt")
        assertJavaSyntaxValid(result.fileName, result.content)
    }

    @Test
    fun `flow nodes are singletons that cannot be instantiated`() {
        // given: the bike-leasing model
        val modelApi = testProcessModelApi(model = testBikeLeasingModel())

        // when: we build the process API file
        val result = underTest.buildApiFile(modelApi)

        // then: every node is only reachable through its INSTANCE
        val content = result.content
        assertThat(content).doesNotContainPattern("(?<!INSTANCE = )new (?!Next|Start)\\w+\\(\\)")
        assertThat(content).contains("private BusinessRuleTaskCheckCreditRating()")
        assertThat(content).contains("private Variables()")
        assertThat(content).contains("public static final BusinessRuleTaskCheckCreditRating INSTANCE = new BusinessRuleTaskCheckCreditRating();")
        assertThat(content).contains("return BusinessRuleTaskCheckCreditRating.INSTANCE;")
        assertThat(content).doesNotContain("import java.lang.")
        assertJavaSyntaxValid(result.fileName, result.content)
    }

    @Test
    fun `maps content of id to valid variable name format`() {
        // given: a model with flow nodes that have slashes in their names
        val defaultModel = testBikeLeasingModel()
        val modifiedNodes = defaultModel.flowNodes.map { it.withId(it.id?.replace("_", "-")) }
        val modelApi = testProcessModelApi(
            model = testBikeLeasingModel(flowNodes = modifiedNodes),
            packagePath = "de.emaarco.example",
        )

        // when: we build the process API file
        val result = underTest.buildApiFile(modelApi)

        // then: expect the generated code contains valid Java
        assertThat(result.content).isNotEmpty()
        assertJavaSyntaxValid(result.fileName, result.content)
    }

    @Test
    fun `several flows to the same element share one SequenceFlows successor`() {
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
        val result = underTest.buildApiFile(testProcessModelApi(model = model, language = OutputLanguage.JAVA))

        // then: one stable name for both flows, carried by one SequenceFlows
        assertThat(result.content).contains("public SequenceFlows<Approve> approve()")
        assertThat(result.content).contains("\"flow_small\"", "\"flow_vip\"")
        assertJavaSyntaxValid(result.fileName, result.content)
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
            outputLanguage = OutputLanguage.JAVA,
            packagePath = "de.emaarco.example",
            targetEngine = ProcessEngine.ZEEBE,
        )

        // when: we build the process API file
        val result = underTest.buildApiFile(modelApi)

        // then: output contains FlowVariants section instead of a flat FlowNodes
        assertMatchesGolden(result.content, "/api/MultiVariantProcessApiJava.txt")
        assertJavaSyntaxValid(result.fileName, result.content)
    }

    private fun assertJavaSyntaxValid(fileName: String, source: String) {
        val compiler = requireNotNull(ToolProvider.getSystemJavaCompiler())
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val fileManager = compiler.getStandardFileManager(diagnostics, null, null)
        val sourceObject = object : SimpleJavaFileObject(
            URI.create("string:///${fileName.replace('.', '/')}"),
            JavaFileObject.Kind.SOURCE,
        ) {
            override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence = source
        }
        val task = compiler.getTask(null, fileManager, diagnostics, null, null, listOf(sourceObject)) as JavacTask
        task.parse()
        val errors = diagnostics.diagnostics.filter { it.kind == Diagnostic.Kind.ERROR }
        assertThat(errors)
            .withFailMessage { "Java syntax errors in generated output: ${errors.map { it.getMessage(null) }}" }
            .isEmpty()
    }
}
