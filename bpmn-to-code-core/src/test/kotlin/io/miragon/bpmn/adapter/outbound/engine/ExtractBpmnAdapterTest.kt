package io.miragon.bpmn.adapter.outbound.engine

import io.miragon.bpmn.domain.BpmnResource
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.shared.TaskImplementation
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ExtractBpmnAdapterTest {

    private val underTest = ExtractBpmnAdapter()

    @Test
    fun `extract reads the model with the dialect registered for the engine`() {

        // given: the Camunda 8 newsletter model
        val bpmnResource = classpathResource("c8-subscribe-newsletter.bpmn")

        // when: extracting for Zeebe
        val result = underTest.extract(bpmnFile = bpmnResource, engine = ProcessEngine.ZEEBE)

        // then: the model carries the job-worker implementations only the Zeebe dialect produces
        assertThat(result.processId).isEqualTo("newsletterSubscription")
        assertThat(result.serviceTasks.map { it.implementation })
            .contains(TaskImplementation.JobWorker("newsletter.sendWelcomeMail"))
    }

    @Test
    fun `extract throws when no dialect is registered for the engine`() {

        // given: an adapter that only knows Zeebe
        val zeebeOnly = ExtractBpmnAdapter(dialects = ExtractBpmnAdapter.dialects.filterKeys { it == ProcessEngine.ZEEBE })
        val bpmnResource = classpathResource("c7-subscribe-newsletter.bpmn")

        // when / then: an exception is thrown
        assertThatThrownBy { zeebeOnly.extract(bpmnFile = bpmnResource, engine = ProcessEngine.CAMUNDA_7) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `extract names the offending file when the model cannot be read`() {

        // given: a well-formed BPMN file that declares no process
        val bpmnResource = BpmnResource(fileName = "no-process.bpmn", content = DEFINITIONS_WITHOUT_PROCESS.toByteArray())

        // when / then: the failure is wrapped and points at the file
        assertThatThrownBy { underTest.extract(bpmnFile = bpmnResource, engine = ProcessEngine.ZEEBE) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("no-process.bpmn")
    }

    @Test
    fun `a malformed file is reported with its name, not as a security violation`() {

        // given: a truncated BPMN file
        val bpmnResource = BpmnResource(fileName = "truncated.bpmn", content = "<bpmn:definitions".toByteArray())

        // when / then: the reader's parse failure reaches the caller with the file that caused it
        assertThatThrownBy { underTest.extract(bpmnFile = bpmnResource, engine = ProcessEngine.ZEEBE) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("truncated.bpmn")
    }

    private fun classpathResource(fileName: String): BpmnResource {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/$fileName"))
        return BpmnResource(fileName = fileName, content = File(resourceUrl.toURI()).readBytes())
    }

    private companion object {
        private val DEFINITIONS_WITHOUT_PROCESS = """
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" id="Definitions_1"
                              targetNamespace="http://bpmn.io/schema/bpmn">
              <bpmn:message id="Message_1" name="orphan" />
            </bpmn:definitions>
        """.trimIndent()
    }
}
