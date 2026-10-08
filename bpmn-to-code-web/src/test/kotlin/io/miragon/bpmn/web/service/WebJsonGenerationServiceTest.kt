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
    fun `should reject files sharing a process id that nothing tells apart`() {
        // given: the same process uploaded twice
        val privateXml = String(Base64.getDecoder().decode(loadSampleBase64("examples/zeebe-bike-leasing.bpmn")))
        val request = requestOfPrivateAnd(corporateXml = privateXml)

        // when: generating JSON
        val response = underTest.generate(request)

        // then: a bad request names both files and the way to tell them apart
        assertThat(response.success).isFalse()
        assertThat(response.statusCode).isEqualTo(HttpStatusCode.BadRequest)
        assertThat(response.error).contains("'bikeLeasing.json'", "corporate (process id 'bikeLeasing'), private (process id 'bikeLeasing')", "variantName")
    }

    @Test
    fun `should generate a JSON per file once a variant name tells files sharing a process id apart`() {
        // given: the same process uploaded twice, one of the files declaring a variant name
        val privateXml = String(Base64.getDecoder().decode(loadSampleBase64("examples/zeebe-bike-leasing.bpmn")))
        val variantProperty = """<zeebe:properties><zeebe:property name="variantName" value="corporate" /></zeebe:properties>"""
        val corporateXml = privateXml.replace(Regex("<bpmn:process [^>]*>")) { "${it.value}<bpmn:extensionElements>$variantProperty</bpmn:extensionElements>" }
        val request = requestOfPrivateAnd(corporateXml = corporateXml)

        // when: generating JSON
        val response = underTest.generate(request)

        // then: each file is a JSON of its own for the same process id
        assertThat(response.success).describedAs("Generation should succeed but got: ${response.error}").isTrue()
        assertThat(response.files.map { it.fileName }).containsExactly("bikeLeasing.json", "corporate_bikeLeasing.json")
        assertThat(response.files.map { it.processId }).containsExactly("bikeLeasing", "bikeLeasing")
    }

    private fun requestOfPrivateAnd(corporateXml: String): GenerateJsonRequest {
        val privateBase64 = loadSampleBase64("examples/zeebe-bike-leasing.bpmn")
        val corporateBase64 = Base64.getEncoder().encodeToString(corporateXml.encodeToByteArray())
        return GenerateJsonRequest(
            files = listOf(
                BpmnFileData(fileName = "corporate.bpmn", content = corporateBase64),
                BpmnFileData(fileName = "private.bpmn", content = privateBase64),
            ),
            config = GenerateJsonRequest.JsonGenerationConfig(processEngine = ProcessEngine.ZEEBE),
        )
    }

    private fun loadSampleBase64(resourcePath: String): String {
        val bytes = requireNotNull(javaClass.classLoader.getResourceAsStream(resourcePath)) {
            "Could not find resource: $resourcePath"
        }.readBytes()
        return Base64.getEncoder().encodeToString(bytes)
    }
}
