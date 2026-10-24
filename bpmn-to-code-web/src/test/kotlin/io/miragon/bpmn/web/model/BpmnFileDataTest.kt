package io.miragon.bpmn.web.model

import io.miragon.bpmn.domain.BpmnReadException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.Base64

class BpmnFileDataTest {

    private val underTest = BpmnFileData(
        fileName = "order-process.bpmn",
        content = Base64.getEncoder().encodeToString("<definitions/>".toByteArray()),
    )

    @Test
    fun `decodes the uploaded BPMN XML`() {
        assertThat(underTest.bpmnXml()).isEqualTo("<definitions/>")
    }

    @Test
    fun `rejects content that is not base64 as an unreadable file`() {
        val broken = BpmnFileData(fileName = "broken.bpmn", content = "not-valid-base64!!!")

        assertThatThrownBy { broken.bpmnXml() }.isInstanceOf(BpmnReadException::class.java).hasMessageContaining("broken.bpmn")
    }
}
