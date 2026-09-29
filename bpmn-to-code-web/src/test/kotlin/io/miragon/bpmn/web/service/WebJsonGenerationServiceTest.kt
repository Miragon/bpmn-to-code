package io.miragon.bpmn.web.service

import io.ktor.http.HttpStatusCode
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.web.model.BpmnFileData
import io.miragon.bpmn.web.model.GenerateJsonRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Base64

class WebJsonGenerationServiceTest {

    private val underTest = WebJsonGenerationService()

    @Test
    fun `should generate JSON from sample BPMN file`() {
        // given: the sample BPMN served by the web app
        val request = GenerateJsonRequest(
            files = listOf(
                BpmnFileData(
                    fileName = "zeebe-bike-leasing.bpmn",
                    content = loadSampleBase64("examples/zeebe-bike-leasing.bpmn"),
                ),
            ),
            config = GenerateJsonRequest.JsonGenerationConfig(processEngine = ProcessEngine.ZEEBE),
        )

        // when: generating JSON
        val response = underTest.generate(request)

        // then: generation succeeds
        assertThat(request.files.first().fileName).isEqualTo("zeebe-bike-leasing.bpmn")
        assertThat(response.success).describedAs("Generation should succeed but got: ${response.error}").isTrue()
        assertThat(response.files).isNotEmpty()
        assertThat(response.error).isNull()
        val file = response.files.first()
        assertThat(file.fileName).endsWith(".json")
        assertThat(file.processId).isEqualTo("bikeLeasing")
        assertThat(file.content).describedAs("Should carry the generated JSON body").isNotBlank()
    }

    @Test
    fun `should return error response when base64 content is invalid`() {
        // given: a request with invalid Base64 content
        val request = GenerateJsonRequest(
            files = listOf(
                BpmnFileData(fileName = "invalid.bpmn", content = "not-valid-base64!!!"),
            ),
            config = GenerateJsonRequest.JsonGenerationConfig(processEngine = ProcessEngine.ZEEBE),
        )

        // when: generating JSON
        val response = underTest.generate(request)

        // then: the response indicates failure with an error message
        assertThat(response.success).isFalse()
        assertThat(response.error).isNotNull()
        assertThat(response.files).isEmpty()
    }

    @Test
    fun `should reject files sharing a process id unless variants are enabled`() {
        // given: two variants of the same process without enabling variants
        val request = variantRequest(enableVariants = false)

        // when: generating JSON
        val response = underTest.generate(request)

        // then: a bad request names both files and the opt-in flag
        assertThat(response.success).isFalse()
        assertThat(response.statusCode).isEqualTo(HttpStatusCode.BadRequest)
        assertThat(response.error).contains("corporate, private", "enableVariants")
    }

    @Test
    fun `should merge files sharing a process id into one JSON when variants are enabled`() {
        // given: two variants of the same process with variants enabled
        val request = variantRequest(enableVariants = true)

        // when: generating JSON
        val response = underTest.generate(request)

        // then: one JSON file for the merged process
        assertThat(response.success).describedAs("Generation should succeed but got: ${response.error}").isTrue()
        assertThat(response.files).hasSize(1)
    }

    private fun variantRequest(enableVariants: Boolean): GenerateJsonRequest {
        val corporateXml = String(Base64.getDecoder().decode(loadSampleBase64("examples/zeebe-bike-leasing.bpmn")))
        val privateXml = corporateXml.replace("name=\"variantName\" value=\"corporate\"", "name=\"variantName\" value=\"private\"")
        return GenerateJsonRequest(
            files = listOf(
                BpmnFileData(fileName = "corporate.bpmn", content = Base64.getEncoder().encodeToString(corporateXml.encodeToByteArray())),
                BpmnFileData(fileName = "private.bpmn", content = Base64.getEncoder().encodeToString(privateXml.encodeToByteArray())),
            ),
            config = GenerateJsonRequest.JsonGenerationConfig(
                processEngine = ProcessEngine.ZEEBE,
                enableVariants = enableVariants,
            ),
        )
    }

    private fun loadSampleBase64(resourcePath: String): String {
        val bytes = requireNotNull(javaClass.classLoader.getResourceAsStream(resourcePath)) {
            "Could not find resource: $resourcePath"
        }.readBytes()
        return Base64.getEncoder().encodeToString(bytes)
    }
}
