package io.miragon.bpmn.web.service

import io.ktor.http.HttpStatusCode
import io.miragon.bpmn.domain.ProcessApiNamingException
import io.miragon.bpmn.domain.validation.BpmnValidationException
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.ValidationViolation
import io.miragon.bpmn.web.model.BpmnFileData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class GenerationGuardTest {

    @Test
    fun `answers with the generation when it succeeds`() {
        val answer = GenerationGuard.run(files = files(count = 3), failure = ::Answer) { Answer(status = HttpStatusCode.OK, error = null) }

        assertThat(answer).isEqualTo(Answer(status = HttpStatusCode.OK, error = null))
    }

    @Test
    fun `rejects a request without files before generating`() {
        val answer = GenerationGuard.run(files = files(count = 0), failure = ::Answer) { error("must not generate") }

        assertThat(answer).isEqualTo(Answer(status = HttpStatusCode.BadRequest, error = "No files provided"))
    }

    @Test
    fun `rejects more than three files before generating`() {
        val answer = GenerationGuard.run(files = files(count = 4), failure = ::Answer) { error("must not generate") }

        assertThat(answer).isEqualTo(Answer(status = HttpStatusCode.BadRequest, error = "Maximum 3 BPMN files allowed"))
    }

    @Test
    fun `answers a model the validation rejects with its violations`() {
        val exception = BpmnValidationException(listOf(VIOLATION))

        val answer = GenerationGuard.run(files = files(count = 1), failure = ::Answer) { throw exception }

        assertThat(answer).isEqualTo(Answer(status = HttpStatusCode.BadRequest, error = exception.message))
    }

    @Test
    fun `answers files sharing a process id with the conflict`() {
        val exception = ProcessApiNamingException("'OrderProcessApi' would be generated from several BPMN files: a.bpmn, b.bpmn.")

        val answer = GenerationGuard.run(files = files(count = 2), failure = ::Answer) { throw exception }

        assertThat(answer).isEqualTo(Answer(status = HttpStatusCode.BadRequest, error = exception.message))
    }

    @ParameterizedTest
    @MethodSource("generatorFailures")
    fun `answers any other failure of the generator as an unknown error`(exception: RuntimeException) {
        val answer = GenerationGuard.run(files = files(count = 1), failure = ::Answer) { throw exception }

        assertThat(answer).isEqualTo(Answer(status = HttpStatusCode.InternalServerError, error = "Unknown error occurred"))
    }

    private fun files(count: Int): List<BpmnFileData> = List(count) { BpmnFileData(fileName = "process-$it.bpmn", content = "") }

    private data class Answer(val status: HttpStatusCode, val error: String?)

    companion object {

        @JvmStatic
        fun generatorFailures(): List<RuntimeException> = listOf(IllegalStateException("broken"), IllegalArgumentException("broken"))

        private val VIOLATION = ValidationViolation(
            ruleId = "rule-1",
            severity = Severity.ERROR,
            elementId = "task-1",
            processId = "my-process",
            message = "Service task is missing implementation",
        )
    }
}
