package io.miragon.bpmn.web.routes

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.miragon.bpmn.web.config.AppConfig
import io.miragon.bpmn.web.config.CorsConfig
import io.miragon.bpmn.web.config.LegalLinksConfig
import io.miragon.bpmn.web.configureApp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.Base64

class GenerationRoutesTest {

    @Test
    fun `generates the process api and bundles the runtime sources`() {
        testApplication {
            application { configureApp(appConfig()) }

            val response = client.post("/api/generate") { jsonBody(requestOf(bikeLeasing())) }

            val body = jsonOf(response.bodyAsText())
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            assertThat(body.getValue("success").jsonPrimitive.boolean).isTrue()
            assertThat(fileNamesIn(body, "files")).contains("BikeLeasingProcessApi.kt", "ServiceTasks.kt")
            assertThat(fileNamesIn(body, "libraryFiles")).isNotEmpty()
        }
    }

    @Test
    fun `generates the process json`() {
        testApplication {
            application { configureApp(appConfig()) }

            val response = client.post("/api/generate-json") { jsonBody(requestOf(bikeLeasing())) }

            val body = jsonOf(response.bodyAsText())
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            assertThat(body.getValue("success").jsonPrimitive.boolean).isTrue()
            assertThat(fileNamesIn(body, "files")).isNotEmpty().allMatch { it.endsWith(".json") }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["/api/generate", "/api/generate-json"])
    fun `rejects a request without files`(endpoint: String) {
        testApplication {
            application { configureApp(appConfig()) }

            val response = client.post(endpoint) { jsonBody(requestOf()) }

            assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
            assertThat(jsonOf(response.bodyAsText())).isEqualTo(failure(error = "No files provided"))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["/api/generate", "/api/generate-json"])
    fun `rejects more than three files`(endpoint: String) {
        testApplication {
            application { configureApp(appConfig()) }
            val files = (1..4).map { bikeLeasing(fileName = "bike-leasing-$it.bpmn") }

            val response = client.post(endpoint) { jsonBody(requestOf(*files.toTypedArray())) }

            assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
            assertThat(jsonOf(response.bodyAsText())).isEqualTo(failure(error = "Maximum 3 BPMN files allowed"))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["/api/generate", "/api/generate-json"])
    fun `answers files sharing a process id with the conflict`(endpoint: String) {
        testApplication {
            application { configureApp(appConfig()) }
            val files = arrayOf(bikeLeasing(fileName = "bike-leasing-a.bpmn"), bikeLeasing(fileName = "bike-leasing-b.bpmn"))

            val response = client.post(endpoint) { jsonBody(requestOf(*files)) }

            val body = jsonOf(response.bodyAsText())
            assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
            assertThat(body.keys).containsExactlyInAnyOrder("success", "files", "error")
            assertThat(body.getValue("error").jsonPrimitive.content).contains("variantName")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["/api/generate", "/api/generate-json"])
    fun `ignores the enableVariants setting of clients written against earlier versions`(endpoint: String) {
        testApplication {
            application { configureApp(appConfig()) }
            val request = buildJsonObject {
                put("files", JsonArray(listOf(bikeLeasing())))
                putJsonObject("config") {
                    put("outputLanguage", "KOTLIN")
                    put("processEngine", "ZEEBE")
                    put("enableVariants", false)
                }
            }

            val response = client.post(endpoint) { jsonBody(request) }

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["/api/generate", "/api/generate-json"])
    fun `answers an invalid model with its violations`(endpoint: String) {
        testApplication {
            application { configureApp(appConfig()) }

            val response = client.post(endpoint) { jsonBody(requestOf(bpmnFile(fileName = "reserved.bpmn", bpmnXml = RESERVED_ELEMENT_NAME))) }

            val body = jsonOf(response.bodyAsText())
            assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
            assertThat(body.keys).containsExactlyInAnyOrder("success", "files", "error")
            assertThat(body.getValue("error").jsonPrimitive.content).contains("reserved-element-name")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["/api/generate", "/api/generate-json"])
    fun `answers content that is not base64 with an unknown error`(endpoint: String) {
        testApplication {
            application { configureApp(appConfig()) }
            val file = buildJsonObject {
                put("fileName", "broken.bpmn")
                put("content", "not-valid-base64!!!")
            }

            val response = client.post(endpoint) { jsonBody(requestOf(file)) }

            assertThat(response.status).isEqualTo(HttpStatusCode.InternalServerError)
            assertThat(jsonOf(response.bodyAsText())).isEqualTo(failure(error = "Unknown error occurred"))
        }
    }

    private fun HttpRequestBuilder.jsonBody(body: JsonObject) {
        contentType(ContentType.Application.Json)
        setBody(body.toString())
    }

    private fun jsonOf(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun fileNamesIn(body: JsonObject, key: String): List<String> = body.getValue(key).jsonArray.map { it.jsonObject.getValue("fileName").jsonPrimitive.content }

    private fun requestOf(vararg files: JsonObject) = buildJsonObject {
        put("files", JsonArray(files.toList()))
        putJsonObject("config") {
            put("outputLanguage", "KOTLIN")
            put("processEngine", "ZEEBE")
        }
    }

    private fun bikeLeasing(fileName: String = "bike-leasing.bpmn") = bpmnFile(
        fileName = fileName,
        bpmnXml = requireNotNull(javaClass.getResource("/bpmn/zeebe/bike-leasing.bpmn")).readText(),
    )

    private fun bpmnFile(fileName: String, bpmnXml: String) = buildJsonObject {
        put("fileName", fileName)
        put("content", Base64.getEncoder().encodeToString(bpmnXml.toByteArray()))
    }

    private fun failure(error: String) = buildJsonObject {
        put("success", false)
        putJsonArray("files") {}
        put("error", error)
    }

    private fun appConfig() = AppConfig(
        legalLinks = LegalLinksConfig(imprintUrl = null, privacyUrl = null),
        cors = CorsConfig(allowedOrigins = listOf("*")),
        port = 8080,
        version = "1.2.3",
    )

    companion object {

        private val RESERVED_ELEMENT_NAME = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" id="definitions" targetNamespace="http://bpmn.io/schema/bpmn">
              <bpmn:process id="approval" isExecutable="true">
                <bpmn:startEvent id="next" />
              </bpmn:process>
            </bpmn:definitions>
        """.trimIndent()
    }
}
