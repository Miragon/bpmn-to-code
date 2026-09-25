package io.miragon.bpmn.domain.service

import io.miragon.bpmn.domain.jobWorkerTask
import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.testProcessModel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SharedDefinitionsServiceTest {

    private val underTest = SharedDefinitionsService()

    @Test
    fun `collect keeps one entry for a job type and message shared by two processes`() {
        // given: two processes using the same job type and message
        val first = testProcessModel(
            processId = "first",
            flowNodes = listOf(jobWorkerTask(id = "Task_A", jobType = "newsletter.sendMail")),
            messages = listOf(RootElementDefinition.Message(id = "Message_A", name = "Message_FormSubmitted")),
        )
        val second = testProcessModel(
            processId = "second",
            flowNodes = listOf(jobWorkerTask(id = "Task_B", jobType = "newsletter.sendMail")),
            messages = listOf(RootElementDefinition.Message(id = "Message_B", name = "Message_FormSubmitted")),
        )

        // when: collecting the shared definitions
        val result = underTest.collect(listOf(first, second))

        // then: each identifier appears once
        assertThat(result.serviceTasks.map { it.getValue() }).containsExactly("newsletter.sendMail")
        assertThat(result.messages.map { it.getValue() }).containsExactly("Message_FormSubmitted")
    }

    @Test
    fun `collect keeps errors with the same name and different codes apart`() {
        // given: two errors sharing a name
        val model = testProcessModel(
            errors = listOf(
                RootElementDefinition.Error(id = "Error_1", name = "InvalidMail", code = "500"),
                RootElementDefinition.Error(id = "Error_2", name = "InvalidMail", code = "400"),
            ),
        )

        // when: collecting the shared definitions
        val result = underTest.collect(listOf(model))

        // then: both errors are kept, sorted
        assertThat(result.errors.map { it.getName() }).containsExactly("INVALID_MAIL_400", "INVALID_MAIL_500")
    }

    @Test
    fun `collect skips definitions without a name`() {
        // given: a signal without a name
        val model = testProcessModel(signals = listOf(RootElementDefinition.Signal(id = "Signal_1", name = null)))

        // when: collecting the shared definitions
        val result = underTest.collect(listOf(model))

        // then: nothing is collected
        assertThat(result.signals).isEmpty()
    }

    @Test
    fun `collect keeps one entry for root elements of one process that share a name`() {
        // given: two message root elements with the same name and their own ids
        val model = testProcessModel(
            messages = listOf(
                RootElementDefinition.Message(id = "Message_1", name = "Message_FormSubmitted"),
                RootElementDefinition.Message(id = "Message_2", name = "Message_FormSubmitted"),
            ),
        )

        // when: collecting the shared definitions
        val result = underTest.collect(listOf(model))

        // then: a duplicate constant would not compile, so only one entry remains
        assertThat(result.messages.map { it.getValue() }).containsExactly("Message_FormSubmitted")
    }
}
