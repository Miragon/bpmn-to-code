package io.miragon.bpmn.web.model

import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class GenerateResponse(
    val success: Boolean,
    val files: List<GeneratedFile>,
    val libraryFiles: List<GeneratedFile> = emptyList(),
    val runtimeDependency: RuntimeDependency? = null,
    val error: String? = null,
    @Transient val statusCode: HttpStatusCode = HttpStatusCode.OK,
) {

    @Serializable
    data class GeneratedFile(val fileName: String, val content: String, val processId: String?)

    @Serializable
    data class RuntimeDependency(
        val group: String,
        val artifact: String,
        val version: String,
        val gradleSnippet: String,
        val mavenSnippet: String,
    )

    companion object {
        fun failure(statusCode: HttpStatusCode, error: String?) = GenerateResponse(
            success = false,
            files = emptyList(),
            error = error,
            statusCode = statusCode,
        )
    }
}
