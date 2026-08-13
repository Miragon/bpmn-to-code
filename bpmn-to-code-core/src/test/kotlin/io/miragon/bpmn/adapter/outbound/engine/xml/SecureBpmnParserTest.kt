package io.miragon.bpmn.adapter.outbound.engine.xml

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
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
            .isInstanceOf(SecurityException::class.java)
            .hasMessageContaining("DOCTYPE")
    }

    @Test
    fun `reports malformed XML as malformed, not as a DOCTYPE violation`() {
        // given: a file that is not well-formed XML at all
        val truncated = "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"".encodeToByteArray()

        // then: the failure names the real problem — calling it a security violation sends the reader
        // looking for a DOCTYPE that is not there
        assertThatThrownBy { SecureBpmnParser.readModelFromBytes(truncated) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .isNotInstanceOf(SecurityException::class.java)
    }

    @Test
    fun `reports an empty file as malformed`() {
        // given: an empty file, the shape a failed download or an empty resource takes
        assertThatThrownBy { SecureBpmnParser.readModelFromBytes(ByteArray(0)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `parses valid BPMN files without DOCTYPE`() {
        val bytes = requireNotNull(javaClass.classLoader.getResourceAsStream("bpmn/c8-subscribe-newsletter.bpmn")).readBytes()
        assertThatCode { SecureBpmnParser.readModelFromBytes(bytes) }
            .doesNotThrowAnyException()
    }
}
