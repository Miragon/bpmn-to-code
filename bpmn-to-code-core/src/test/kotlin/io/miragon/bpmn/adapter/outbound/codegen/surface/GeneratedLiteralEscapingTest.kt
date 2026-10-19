package io.miragon.bpmn.adapter.outbound.codegen.surface

import io.miragon.bpmn.application.port.inbound.GenerateProcessApiInMemoryUseCase
import io.miragon.bpmn.application.service.GenerateProcessApiInMemoryService
import io.miragon.bpmn.domain.BpmnResource
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.runtime.FlowNode
import io.miragon.bpmn.runtime.HasSuccessors
import io.miragon.bpmn.runtime.SequenceFlows
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Names and expressions from a BPMN file end up in string literals. Whatever characters they contain, the compiled
 * API has to return them unchanged: quotes and backslashes must not end the literal, and `$` must not start a Kotlin
 * string template.
 */
class GeneratedLiteralEscapingTest {

    private val service = GenerateProcessApiInMemoryService()

    @ParameterizedTest
    @EnumSource(value = OutputLanguage::class, names = ["KOTLIN", "JAVA"])
    fun `names and expressions survive compilation unchanged`(language: OutputLanguage) {
        // when: an API is generated from a model whose name and condition contain every character that needs escaping
        val generated = generate(language)
        val classLoader = when (language) {
            OutputLanguage.KOTLIN -> GeneratedSourceCompiler.compileKotlin(generated)
            else -> GeneratedSourceCompiler.compileJava(generated)
        }

        // then: the compiled API returns them unchanged
        val task = nodeOf(classLoader, "TaskEscape")
        assertThat(task.name).isEqualTo(TRICKY_NAME)
        val start = nodeOf(classLoader, "StartEvent") as HasSuccessors<*>
        assertThat(conditionOf(start.next!!)).isEqualTo(TRICKY_EXPRESSION)
    }

    private fun generate(language: OutputLanguage): List<GeneratedApiFile> {
        val command = GenerateProcessApiInMemoryUseCase.Command(
            resources = listOf(BpmnResource(fileName = "escaping.bpmn", content = escapingModel().encodeToByteArray())),
            packagePath = "de.gen",
            outputLanguage = language,
            engine = ProcessEngine.ZEEBE,
        )
        return service.generateProcessApi(command)
    }

    private fun nodeOf(classLoader: ClassLoader, nodeName: String): FlowNode {
        val nodeClass = classLoader.loadClass("de.gen.EscapingProcessApi\$FlowNodes\$$nodeName")
        return nodeClass.getField("INSTANCE").get(null) as FlowNode
    }

    private fun conditionOf(next: Any): String? {
        val successor = next.javaClass.methods.single { it.parameterCount == 0 && it.returnType == SequenceFlows::class.java }
        val flows = successor.invoke(next) as SequenceFlows<*>
        return flows.flow.conditionExpression
    }

    private fun escapingModel(): String {
        val model = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" id="definitions" targetNamespace="http://bpmn.io/schema/bpmn">
              <bpmn:process id="escaping" isExecutable="true">
                <bpmn:startEvent id="startEvent"><bpmn:outgoing>flow_start</bpmn:outgoing></bpmn:startEvent>
                <bpmn:task id="task_escape" name="NAME"><bpmn:incoming>flow_start</bpmn:incoming></bpmn:task>
                <bpmn:sequenceFlow id="flow_start" sourceRef="startEvent" targetRef="task_escape">
                  <bpmn:conditionExpression>CONDITION</bpmn:conditionExpression>
                </bpmn:sequenceFlow>
              </bpmn:process>
            </bpmn:definitions>
        """.trimIndent()
        return model.replace("NAME", xmlAttribute(TRICKY_NAME)).replace("CONDITION", TRICKY_EXPRESSION.replace("&", "&amp;").replace("<", "&lt;"))
    }

    private fun xmlAttribute(value: String): String = value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")

    private companion object {
        const val TRICKY_NAME = "quote \" backslash \\ dollar $ template \${x} double \$\${y} raw \"\"\""

        // The reader folds white space in names, but keeps it in expressions.
        const val TRICKY_EXPRESSION = "$TRICKY_NAME tab\tline\nseparator\u2028end"
    }
}
