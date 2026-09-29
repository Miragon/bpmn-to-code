package io.miragon.bpmn.web

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.miragon.bpmn.web.config.AppConfig
import io.miragon.bpmn.web.config.CorsConfig
import io.miragon.bpmn.web.config.LegalLinksConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApplicationTest {

    @Test
    fun `reports the service as healthy`() {
        testApplication {
            application { configureApp(appConfig()) }

            val response = client.get("/health")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            assertThat(response.bodyAsText()).contains("\"status\": \"UP\"")
        }
    }

    @Test
    fun `serves the configuration as json`() {
        testApplication {
            application { configureApp(appConfig()) }

            val response = client.get("/api/config")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            assertThat(response.bodyAsText()).contains("\"version\": \"1.2.3\"", "\"imprintUrl\": \"https://example.com/imprint\"")
        }
    }

    @Test
    fun `redirects the root to the frontend, which is served as a static resource`() {
        testApplication {
            application { configureApp(appConfig()) }
            val client = createClient { followRedirects = false }

            val redirect = client.get("/")
            val frontend = client.get("/static/index.html")

            assertThat(redirect.status).isEqualTo(HttpStatusCode.Found)
            assertThat(redirect.headers[HttpHeaders.Location]).isEqualTo("/static/index.html")
            assertThat(frontend.status).isEqualTo(HttpStatusCode.OK)
        }
    }

    @Test
    fun `allows any origin when no origins are configured`() {
        testApplication {
            application { configureApp(appConfig(allowedOrigins = listOf("*"))) }

            val response = client.options("/api/generate", preflightFrom(origin = "https://any.example.com"))

            assertThat(response.headers[HttpHeaders.AccessControlAllowOrigin]).isEqualTo("*")
        }
    }

    @Test
    fun `allows only the configured origins`() {
        testApplication {
            application { configureApp(appConfig(allowedOrigins = listOf("https://app.example.com"))) }

            val allowed = client.options("/api/generate", preflightFrom(origin = "https://app.example.com"))
            val rejected = client.options("/api/generate", preflightFrom(origin = "https://other.example.com"))

            assertThat(allowed.headers[HttpHeaders.AccessControlAllowOrigin]).isEqualTo("https://app.example.com")
            assertThat(rejected.status).isEqualTo(HttpStatusCode.Forbidden)
        }
    }

    @Test
    fun `answers an unhandled exception with a json error`() {
        testApplication {
            application {
                configureApp(appConfig())
                routing { get("/failing") { error("Boom") } }
            }

            val response = client.get("/failing")

            assertThat(response.status).isEqualTo(HttpStatusCode.InternalServerError)
            assertThat(response.bodyAsText()).contains("\"error\": \"Boom\"")
        }
    }

    private fun preflightFrom(origin: String): HttpRequestBuilder.() -> Unit = {
        header(HttpHeaders.Origin, origin)
        header(HttpHeaders.AccessControlRequestMethod, "POST")
    }

    private fun appConfig(allowedOrigins: List<String> = listOf("*")) = AppConfig(
        legalLinks = LegalLinksConfig(imprintUrl = "https://example.com/imprint", privacyUrl = null),
        cors = CorsConfig(allowedOrigins = allowedOrigins),
        port = 8080,
        version = "1.2.3",
    )
}
