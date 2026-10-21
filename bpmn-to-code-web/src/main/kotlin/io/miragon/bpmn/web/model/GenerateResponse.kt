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

    /**
     * The package the generated code is built from, with one snippet per way of adding it to a build.
     */
    @Serializable
    data class RuntimeDependency(
        val name: String,
        val importedFrom: String,
        val version: String,
        val snippets: List<Snippet>,
    )

    @Serializable
    data class Snippet(val label: String, val code: String)

    companion object {
        fun failure(statusCode: HttpStatusCode, error: String?) = GenerateResponse(
            success = false,
            files = emptyList(),
            error = error,
            statusCode = statusCode,
        )
    }
}
