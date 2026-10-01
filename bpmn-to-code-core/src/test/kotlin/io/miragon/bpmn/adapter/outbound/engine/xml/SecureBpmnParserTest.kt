package io.miragon.bpmn.adapter.outbound.engine.xml

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.camunda.bpm.model.bpmn.instance.BoundaryEvent
import org.camunda.bpm.model.xml.ModelException
import org.junit.jupiter.api.Test

class SecureBpmnParserTest {

    @Test
    fun `rejects BPMN files containing DOCTYPE declarations`() {
        val malicious = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"/>
        """.trimIndent().encodeToByteArray()

        assertThatThrownBy { SecureBpmnParser.readModelFromBytes(malicious) }
            .isInstanceOf(SecurityException::class.java).hasMessageContaining("DOCTYPE")
    }

    @Test
    fun `reports malformed XML as malformed, not as a DOCTYPE violation`() {
        // given: a file that is not well-formed XML at all
        val truncated = "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"".encodeToByteArray()

        // then: the failure names the real problem — calling it a security violation sends the reader
        // looking for a DOCTYPE that is not there
        assertThatThrownBy { SecureBpmnParser.readModelFromBytes(truncated) }
            .isInstanceOf(IllegalArgumentException::class.java).isNotInstanceOf(SecurityException::class.java)
    }

    @Test
    fun `reports an empty file as malformed`() {
        // given: an empty file, the shape a failed download or an empty resource takes
        assertThatThrownBy { SecureBpmnParser.readModelFromBytes(ByteArray(0)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `parses valid BPMN files without DOCTYPE`() {
        val bytes = requireNotNull(javaClass.classLoader.getResourceAsStream("bpmn/zeebe/bike-leasing.bpmn")).readBytes()
        assertThatCode { SecureBpmnParser.readModelFromBytes(bytes) }.doesNotThrowAnyException()
    }

    @Test
    fun `rejects well-formed XML that violates the BPMN schema`() {
        // given: well-formed XML with an element the BPMN schema does not know
        val invalid = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" id="definitions" targetNamespace="http://bpmn.io/schema/bpmn">
              <bpmn:process id="process" isExecutable="true">
                <bpmn:notABpmnElement id="unknown" />
              </bpmn:process>
            </bpmn:definitions>
        """.trimIndent().encodeToByteArray()

        // then: the schema violation is reported
        assertThatThrownBy { SecureBpmnParser.readModelFromBytes(invalid) }
            .isInstanceOf(ModelException::class.java).hasStackTraceContaining("notABpmnElement")
    }

    @Test
    fun `resolves elements and references by their id`() {
        // given: the bike-leasing model, whose boundary event is attached to a service task by id
        val bytes = requireNotNull(javaClass.classLoader.getResourceAsStream("bpmn/zeebe/bike-leasing.bpmn")).readBytes()

        // when
        val model = SecureBpmnParser.readModelFromBytes(bytes)

        // then
        val boundaryEvent = model.getModelElementById<BoundaryEvent>("boundary_applicationInvalid")
        assertThat(boundaryEvent.attachedTo.id).isEqualTo("serviceTask_validateApplication")
    }

    @Test
    fun `completes the document with the attribute defaults of the BPMN schema`() {
        // given: a boundary event that leaves cancelActivity to its schema default
        val bytes = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" id="definitions" targetNamespace="http://bpmn.io/schema/bpmn">
              <bpmn:process id="process" isExecutable="true">
                <bpmn:task id="task" />
                <bpmn:boundaryEvent id="boundary" attachedToRef="task" />
              </bpmn:process>
            </bpmn:definitions>
        """.trimIndent().encodeToByteArray()

        // when
        val model = SecureBpmnParser.readModelFromBytes(bytes)

        // then: the XML document itself carries the default, as it did when Camunda parsed with validation
        val boundaryElement = model.getModelElementById<BoundaryEvent>("boundary").domElement
        assertThat(boundaryElement.getAttribute("cancelActivity")).isEqualTo("true")
    }
}
