package io.miragon.bpmn.web.service

import io.ktor.http.HttpStatusCode
import io.miragon.bpmn.domain.BpmnReadException
import io.miragon.bpmn.domain.ProcessApiNamingException
import io.miragon.bpmn.domain.validation.BpmnValidationException
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.ValidationViolation
import io.miragon.bpmn.web.model.BpmnFileData
import io.miragon.bpmn.web.model.GenerateResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class GenerationGuardTest {

    @Test
    fun `answers with the generation when it succeeds`() {
        val answer = GenerationGuard.run(files = files(count = 3)) { SUCCESS }

        assertThat(answer).isEqualTo(SUCCESS)
    }

    @Test
    fun `rejects a request without files before generating`() {
        val answer = GenerationGuard.run(files = files(count = 0)) { error("must not generate") }

        assertThat(answer).isEqualTo(GenerateResponse.failure(statusCode = HttpStatusCode.BadRequest, error = "No files provided"))
    }

    @Test
    fun `rejects more than three files before generating`() {
        val answer = GenerationGuard.run(files = files(count = 4)) { error("must not generate") }

        assertThat(answer).isEqualTo(GenerateResponse.failure(statusCode = HttpStatusCode.BadRequest, error = "Maximum 3 BPMN files allowed"))
    }

    @Test
    fun `answers a model the validation rejects with its violations`() {
        val exception = BpmnValidationException(listOf(VIOLATION))

        val answer = GenerationGuard.run(files = files(count = 1)) { throw exception }

        assertThat(answer).isEqualTo(GenerateResponse.failure(statusCode = HttpStatusCode.BadRequest, error = exception.message))
    }

    @Test
    fun `answers files sharing a process id with the conflict`() {
        val exception = ProcessApiNamingException("'OrderProcessApi' would be generated from several BPMN files: a.bpmn, b.bpmn.")

        val answer = GenerationGuard.run(files = files(count = 2)) { throw exception }

        assertThat(answer).isEqualTo(GenerateResponse.failure(statusCode = HttpStatusCode.BadRequest, error = exception.message))
    }

    @Test
    fun `answers a file that cannot be read with the reason`() {
        val exception = BpmnReadException("Failed to extract file: broken.bpmn. Please check its a valid file for ZEEBE")

        val answer = GenerationGuard.run(files = files(count = 1)) { throw exception }

        assertThat(answer).isEqualTo(GenerateResponse.failure(statusCode = HttpStatusCode.BadRequest, error = exception.message))
    }

    @ParameterizedTest
    @MethodSource("generatorFailures")
    fun `answers any other failure of the generator as an unknown error`(exception: RuntimeException) {
        val answer = GenerationGuard.run(files = files(count = 1)) { throw exception }

        assertThat(answer).isEqualTo(GenerateResponse.failure(statusCode = HttpStatusCode.InternalServerError, error = "Unknown error occurred"))
    }

    private fun files(count: Int): List<BpmnFileData> = List(count) { BpmnFileData(fileName = "process-$it.bpmn", content = "") }

    companion object {

        @JvmStatic
        fun generatorFailures(): List<RuntimeException> = listOf(IllegalStateException("broken"), IllegalArgumentException("broken"))

        private val SUCCESS = GenerateResponse(success = true, files = emptyList())

        private val VIOLATION = ValidationViolation(
            ruleId = "rule-1",
            severity = Severity.ERROR,
            elementId = "task-1",
            processId = "my-process",
            message = "Service task is missing implementation",
        )
    }
}
