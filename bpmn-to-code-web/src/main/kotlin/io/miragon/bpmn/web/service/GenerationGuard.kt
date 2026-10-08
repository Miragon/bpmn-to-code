package io.miragon.bpmn.web.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.miragon.bpmn.domain.ProcessApiNamingException
import io.miragon.bpmn.domain.validation.BpmnValidationException
import io.miragon.bpmn.web.model.BpmnFileData

/**
 * What both generation endpoints do around the generation itself: accept one to [MAX_FILES] files, answer a
 * model the generator rejects with 400 and the reason, and any other failure of the generator with 500.
 */
internal object GenerationGuard {

    private const val MAX_FILES = 3

    private const val UNKNOWN_ERROR = "Unknown error occurred"

    private val logger = KotlinLogging.logger {}

    fun <R> run(files: List<BpmnFileData>, failure: (HttpStatusCode, String?) -> R, generate: () -> R): R {
        if (files.isEmpty()) {
            return failure(HttpStatusCode.BadRequest, "No files provided")
        }
        if (files.size > MAX_FILES) {
            return failure(HttpStatusCode.BadRequest, "Maximum $MAX_FILES BPMN files allowed")
        }
        return try {
            generate()
        } catch (e: BpmnValidationException) {
            logger.error(e) { "BPMN validation failed during generation" }
            failure(HttpStatusCode.BadRequest, e.message)
        } catch (e: ProcessApiNamingException) {
            logger.warn { e.message }
            failure(HttpStatusCode.BadRequest, e.message)
        } catch (e: IllegalStateException) {
            logger.error(e) { "Unexpected error during generation" }
            failure(HttpStatusCode.InternalServerError, UNKNOWN_ERROR)
        } catch (e: IllegalArgumentException) {
            logger.error(e) { "Unexpected error during generation" }
            failure(HttpStatusCode.InternalServerError, UNKNOWN_ERROR)
        }
    }
}
