package io.miragon.bpmn.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class SourcedProcessModelTest {

    @Test
    fun `rejects a process id defined in several files`() {
        val sources = listOf(
            SourcedProcessModel("bike-leasing-v2.bpmn", testProcessModel(processId = "bike-leasing", variantName = "v2")),
            SourcedProcessModel("bike-leasing-v1.bpmn", testProcessModel(processId = "bike-leasing", variantName = "v1")),
            SourcedProcessModel("bike-return.bpmn", testProcessModel(processId = "bike-return")),
        )

        assertThatThrownBy { SourcedProcessModel.requireUniqueProcessIds(sources) }
            .isInstanceOf(DuplicateProcessIdException::class.java)
            .hasMessageContaining("'bike-leasing'")
            .hasMessageContaining("bike-leasing-v1.bpmn, bike-leasing-v2.bpmn").hasMessageContaining("enableVariants")
    }

    @Test
    fun `accepts process ids that are each defined in one file`() {
        val sources = listOf(
            SourcedProcessModel("bike-leasing.bpmn", testProcessModel(processId = "bike-leasing")),
            SourcedProcessModel("bike-return.bpmn", testProcessModel(processId = "bike-return")),
        )

        assertThatCode { SourcedProcessModel.requireUniqueProcessIds(sources) }.doesNotThrowAnyException()
    }

    @Test
    fun `keeps only the processes marked executable`() {
        val executable = SourcedProcessModel("bike-leasing.bpmn", testProcessModel(processId = "bike-leasing"))
        val draft = SourcedProcessModel("draft.bpmn", testProcessModel(processId = "draft").copy(isExecutable = false))

        assertThat(SourcedProcessModel.executableOnly(listOf(executable, draft))).containsExactly(executable)
    }
}
