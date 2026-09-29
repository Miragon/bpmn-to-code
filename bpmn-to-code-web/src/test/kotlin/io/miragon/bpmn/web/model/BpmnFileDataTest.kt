package io.miragon.bpmn.web.model

import org.assertj.core.api.Assertions.assertThat
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
    fun `names the process after the file`() {
        assertThat(underTest.processName()).isEqualTo("order-process")
    }
}
