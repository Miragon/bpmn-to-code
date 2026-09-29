package io.miragon.bpmn.web.model

import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class GenerateJsonResponse(
    val success: Boolean,
    val files: List<GeneratedJsonFileResponse>,
    val error: String? = null,
    @Transient val statusCode: HttpStatusCode = HttpStatusCode.OK,
) {

    @Serializable
    data class GeneratedJsonFileResponse(val fileName: String, val content: String, val processId: String)

    companion object {
        fun failure(statusCode: HttpStatusCode, error: String?) = GenerateJsonResponse(
            success = false,
            files = emptyList(),
            error = error,
            statusCode = statusCode,
        )
    }
}
