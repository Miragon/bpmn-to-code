package io.miragon.bpmn.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ProcessApiNamesTest {

    private val underTest = ProcessApiNames

    @Test
    fun `names an element after its id in PascalCase`() {
        assertThat(underTest.elementName(elementId = "serviceTask_sendContract")).isEqualTo("ServiceTaskSendContract")
    }

    @ParameterizedTest
    @ValueSource(strings = ["FlowNodes", "Next", "Messages", "HashCode", "Id"])
    fun `reserves the names the Process API declares itself`(elementName: String) {
        assertThat(underTest.isReserved(elementName)).isTrue()
    }

    @Test
    fun `keeps any other name free`() {
        assertThat(underTest.isReserved(elementName = "ServiceTaskSendContract")).isFalse()
    }
}
