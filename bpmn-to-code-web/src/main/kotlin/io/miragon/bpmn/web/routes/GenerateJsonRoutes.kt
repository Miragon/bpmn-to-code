@file:OptIn(ExperimentalKtorApi::class)

package io.miragon.bpmn.web.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import io.ktor.utils.io.ExperimentalKtorApi
import io.miragon.bpmn.web.model.GenerateJsonRequest
import io.miragon.bpmn.web.service.WebJsonGenerationService

fun Route.generateJsonRoutes(jsonService: WebJsonGenerationService) {
    post("/api/generate-json") {
        val request = call.receive<GenerateJsonRequest>()
        val result = jsonService.generate(request)
        call.respond(result.statusCode, result)
    }.describe {
        summary = "Generate process JSON"
        description = "Upload BPMN files and generate JSON representations of the process models"
        responses {
            HttpStatusCode.OK { description = "JSON generated successfully" }
            HttpStatusCode.BadRequest { description = "No files provided, or more than 3 files" }
            HttpStatusCode.InternalServerError { description = "Unexpected error during generation" }
        }
    }
}
