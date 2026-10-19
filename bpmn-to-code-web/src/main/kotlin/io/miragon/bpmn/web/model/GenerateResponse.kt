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
     * The runtime library the generated code imports from [importedFrom], published as [name]: one [snippets] entry
     * per way to add it, and the directory its sources go to in a download.
     */
    @Serializable
    data class RuntimeDependency(
        val name: String,
        val version: String,
        val importedFrom: String,
        val sourcesDirectory: String,
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
