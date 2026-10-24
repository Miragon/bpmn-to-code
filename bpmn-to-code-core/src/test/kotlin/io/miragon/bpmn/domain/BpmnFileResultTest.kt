package io.miragon.bpmn.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BpmnFileResultTest {

    private val underTest = BpmnFileResult(processId = "bikeLeasing", sourceFile = "zeebe/bike-leasing.bpmn")

    @Test
    fun `describes the process api and the files it is generated from`() {
        assertThat(underTest.describe()).isEqualTo("Generated: bikeLeasing (from zeebe/bike-leasing.bpmn)")
    }

    @Test
    fun `sums up a run by the number of generated process apis`() {
        assertThat(BpmnFileResult.summaryOf(listOf(underTest))).isEqualTo("BPMN models generated successfully (1 models)")
    }

    @Test
    fun `sums up a run without models`() {
        assertThat(BpmnFileResult.summaryOf(emptyList())).isEqualTo("No BPMN models found")
    }
}
