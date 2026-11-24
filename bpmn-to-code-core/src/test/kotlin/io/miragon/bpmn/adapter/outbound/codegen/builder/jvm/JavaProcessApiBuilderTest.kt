package io.miragon.bpmn.adapter.outbound.codegen.builder.jvm

import com.sun.source.util.JavacTask
import io.miragon.bpmn.adapter.outbound.assertMatchesGolden
import io.miragon.bpmn.adapter.outbound.codegen.CodeGenerationAdapter
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.GatewayKind
import io.miragon.bpmn.domain.shared.MessageReference
import io.miragon.bpmn.domain.shared.MultiInstanceDefinition
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.shared.SequenceFlowDefinition
import io.miragon.bpmn.domain.shared.TaskKind
import io.miragon.bpmn.domain.shared.VariableDefinition
import io.miragon.bpmn.domain.shared.VariableDirection
import io.miragon.bpmn.domain.testBikeLeasingModel
import io.miragon.bpmn.domain.testCancelBikeOrderModel
import io.miragon.bpmn.domain.testProcessApiRequest
import io.miragon.bpmn.domain.testProcessModel
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

    private val underTest = CodeGenerationAdapter()

    @Test
    fun `generateCode generates correct process API file`() {
        // given: the bike-leasing model, which covers every implementation kind of Camunda 7
        val request = testProcessApiRequest(
            packagePath = "de.emaarco.example",
            language = OutputLanguage.JAVA,
            model = testBikeLeasingModel(),
        )

        // when: we build the process API file
        val result = underTest.generateCode(request).single()

        // then: a single model file is returned at the root package
        assertThat(result.fileName).isEqualTo("BikeLeasingProcessApi.java")
        assertThat(result.packagePath).isEqualTo("de.emaarco.example")

        assertMatchesGolden(result.content, "/api/BikeLeasingProcessApiJava.txt")
        assertJavaSyntaxValid(result.fileName, result.content)
    }

    @Test
    fun `flow nodes are singletons that cannot be instantiated`() {
        // given: the bike-leasing model
        val request = testProcessApiRequest(model = testBikeLeasingModel(), language = OutputLanguage.JAVA)

        // when: we build the process API file
        val result = underTest.generateCode(request).single()

        // then: every node is only reachable through its INSTANCE
        val content = result.content
        assertThat(content).doesNotContainPattern("(?<!INSTANCE = )new (?!Next|Start|Variables)\\w+\\(\\)")
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
        val request = testProcessApiRequest(
            model = testBikeLeasingModel(flowNodes = modifiedNodes),
            packagePath = "de.emaarco.example",
            language = OutputLanguage.JAVA,
        )

        // when: we build the process API file
        val result = underTest.generateCode(request).single()

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
        val result = underTest.generateCode(testProcessApiRequest(model = model, language = OutputLanguage.JAVA)).single()

        // then: one stable name for both flows, carried by one SequenceFlows
        assertThat(result.content).contains("public SequenceFlows<Approve> approve()")
        assertThat(result.content).contains("\"flow_small\"", "\"flow_vip\"")
        assertJavaSyntaxValid(result.fileName, result.content)
    }

    @Test
    fun `generateCode leads the API name with the variant name of the model`() {
        // given: a model that declares a variant name
        val request = testProcessApiRequest(
            packagePath = "de.emaarco.example",
            language = OutputLanguage.JAVA,
            model = testCancelBikeOrderModel(variantName = "retail"),
        )

        // when: we build the process API file
        val result = underTest.generateCode(request).single()

        // then: the variant name leads the API name, while the process id stays the one of the model
        assertThat(result.fileName).isEqualTo("RetailCancelBikeOrderProcessApi.java")
        assertMatchesGolden(result.content, "/api/PrefixedCancelBikeOrderProcessApiJava.txt")
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

    @Test
    fun `multi-instance activity exposes how its instances run and which collections they bind`() {
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Activity.Task(
                    id = "serviceTask_orderBike",
                    kind = TaskKind.SERVICE,
                    multiInstance = MultiInstanceDefinition(
                        sequential = true,
                        inputCollection = "=bikeIds",
                        inputElement = "bikeId",
                        outputCollection = "orderedBikes",
                        outputElement = "=orderedBike",
                    ),
                ),
            ),
        )

        val result = underTest.generateCode(testProcessApiRequest(model = model, language = OutputLanguage.JAVA)).single()

        assertThat(result.content).contains("implements MultiInstanceActivity {")
        assertThat(result.content).contains(
            "return new BpmnMultiInstance(MultiInstanceType.SEQUENTIAL, \"=bikeIds\", \"bikeId\", \"orderedBikes\", \"=orderedBike\");",
        )
        assertJavaSyntaxValid(result.fileName, result.content)
    }

    @Test
    fun `receive task exposes the correlation key of its message as modelled, a send task does not`() {
        val contractSigned = MessageReference(messageRef = "message_contractSigned")
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Activity.Task(
                    id = "receiveTask_contractSigned",
                    kind = TaskKind.RECEIVE,
                    message = contractSigned,
                ),
                FlowNodeDefinition.Activity.Task(
                    id = "sendTask_contractSigned",
                    kind = TaskKind.SEND,
                    message = contractSigned,
                ),
            ),
            messages = listOf(
                RootElementDefinition.Message(
                    id = "message_contractSigned",
                    name = "miravelo.contractSigned",
                    correlationKey = "=applicationId",
                ),
            ),
        )

        val request = testProcessApiRequest(model = model, language = OutputLanguage.JAVA)
        val result = underTest.generateCode(request).single()

        assertThat(result.content).contains("public String getCorrelationKey() {\n        return \"=applicationId\";")
        assertThat(result.content.split("getCorrelationKey")).hasSize(2)
        assertJavaSyntaxValid(result.fileName, result.content)
    }
}
