package io.miragon.bpmn.web.model

import io.ktor.http.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GenerateJsonResponseTest {

    @Test
    fun `failure carries the status and the error but no files`() {
        val response = GenerateJsonResponse.failure(statusCode = HttpStatusCode.BadRequest, error = "No files provided")

        assertThat(response.success).isFalse()
        assertThat(response.files).isEmpty()
        assertThat(response.error).isEqualTo("No files provided")
        assertThat(response.statusCode).isEqualTo(HttpStatusCode.BadRequest)
    }
}
