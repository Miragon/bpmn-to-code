package io.miragon.bpmn.application.service

import io.miragon.bpmn.adapter.outbound.codegen.CodeGenerationAdapter
import io.miragon.bpmn.application.port.outbound.ExtractBpmnPort
import io.miragon.bpmn.domain.BpmnModel
import io.miragon.bpmn.domain.BpmnResource
import io.miragon.bpmn.application.port.inbound.GenerateProcessApiInMemoryUseCase
import io.miragon.bpmn.application.port.inbound.GenerateProcessApiInMemoryUseCase.BpmnInput
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.testSendNewsletterBpmnModel
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Guards the property the CI drift-check relies on: generated code is a deterministic function of the
 * `.bpmn` inputs, independent of the order in which the filesystem hands them to us. The loader test
 * covers input ordering; this one covers the merger, feeding the same multi-variant model set through
 * the service in several fixed (non-random) input orders and asserting byte-identical output.
 */
class GenerateProcessApiDeterministicOrderTest {

    private val bpmnService = mockk<ExtractBpmnPort>()
    private val underTest = GenerateProcessApiInMemoryService(
        codeGenerator = CodeGenerationAdapter(),
        bpmnService = bpmnService,
    )

    // Three variants of one process. Same shape, distinct variant names + a distinct node label so the
    // emitted variant blocks and the merged base node differ — a reorder would change bytes if unfixed.
    private val variantNames = listOf("waghaeusel", "default", "kronstorf")

    private val modelsByName: Map<String, BpmnModel> = variantNames.associateWith { name ->
        testSendNewsletterBpmnModel(processId = "sendNewsletter", variantName = name)
            .let { model ->
                model.copy(
                    flowNodes = model.flowNodes.map { node ->
                        if (node.id == "serviceTask_loadSubscribers") node.copy(displayName = "load-$name") else node
                    },
                )
            }
    }

    @Test
    fun `generates byte-identical code regardless of input order`() {

        // given: every permutation we want to exercise (canonical, reversed, rotated)
        val inputOrders = listOf(
            variantNames,
            variantNames.reversed(),
            listOf("default", "waghaeusel", "kronstorf"),
            listOf("kronstorf", "default", "waghaeusel"),
        )

        // when: generating from each order
        val outputs = inputOrders.map { order -> generate(order) }

        // then: all runs produce the exact same files with the exact same content
        val reference = outputs.first()
        assertThat(reference).isNotEmpty()
        outputs.forEach { output ->
            assertThat(output).isEqualTo(reference)
        }
    }

    private fun generate(order: List<String>): List<GeneratedApiFile> {
        order.forEach { name ->
            every { bpmnService.extract(match<BpmnResource> { it.fileName == name }, any()) } returns modelsByName.getValue(name)
        }
        val command = GenerateProcessApiInMemoryUseCase.Command(
            bpmnContents = order.map { BpmnInput(bpmnXml = "<bpmn>$it</bpmn>", processName = it) },
            packagePath = "com.example",
            outputLanguage = OutputLanguage.KOTLIN,
            engine = ProcessEngine.ZEEBE,
        )
        return underTest.generateProcessApi(command)
    }
}
