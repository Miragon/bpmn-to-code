package io.miragon.bpmn.web.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.miragon.bpmn.domain.RejectedModelException
import io.miragon.bpmn.web.model.BpmnFileData
import io.miragon.bpmn.web.model.GenerateResponse

/**
 * What the generation endpoint does around the generation itself: accept one to [MAX_FILES] files, answer a
 * file that cannot be read or a model the generator rejects with 400 and the reason, and any other failure of the
 * generator with 500.
 */
internal object GenerationGuard {

    private const val MAX_FILES = 3

    private const val UNKNOWN_ERROR = "Unknown error occurred"

    private val logger = KotlinLogging.logger {}

    fun run(files: List<BpmnFileData>, generate: () -> GenerateResponse): GenerateResponse {
        if (files.isEmpty()) {
            return GenerateResponse.failure(HttpStatusCode.BadRequest, "No files provided")
        }
        if (files.size > MAX_FILES) {
            return GenerateResponse.failure(HttpStatusCode.BadRequest, "Maximum $MAX_FILES BPMN files allowed")
        }
        return try {
            generate()
        } catch (e: RejectedModelException) {
            logger.warn { e.message }
            GenerateResponse.failure(HttpStatusCode.BadRequest, e.message)
        } catch (e: IllegalStateException) {
            logger.error(e) { "Unexpected error during generation" }
            GenerateResponse.failure(HttpStatusCode.InternalServerError, UNKNOWN_ERROR)
        } catch (e: IllegalArgumentException) {
            logger.error(e) { "Unexpected error during generation" }
            GenerateResponse.failure(HttpStatusCode.InternalServerError, UNKNOWN_ERROR)
        }
    }
}
