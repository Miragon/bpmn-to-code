package io.miragon.bpmn.web.service

import io.ktor.http.HttpStatusCode
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.web.model.BpmnFileData
import io.miragon.bpmn.web.model.GenerateRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Base64

class WebGenerationServiceTest {

    private val underTest = WebGenerationService()

    @Test
    fun `should generate Kotlin API from BPMN file`() {
        // given: a valid Zeebe BPMN file encoded as Base64
        val request = GenerateRequest(
            files = listOf(
                BpmnFileData(
                    fileName = "zeebe-bike-leasing.bpmn",
                    content = loadBpmnBase64("bpmn/zeebe/bike-leasing.bpmn"),
                ),
            ),
            config = GenerateRequest.GenerationConfig(
                outputLanguage = OutputLanguage.KOTLIN,
                processEngine = ProcessEngine.ZEEBE,
            ),
        )

        // when: generating the API
        val response = underTest.generate(request)

        // then: a Kotlin file is generated containing the process constant
        assertThat(request.files.first().fileName).isEqualTo("zeebe-bike-leasing.bpmn")
        assertThat(response.success).describedAs("Generation should succeed").isTrue()
        assertThat(response.files).describedAs("Should generate at least one file").isNotEmpty()
        assertThat(response.error).describedAs("Should not have errors").isNull()
        val generatedFile = response.files.first()
        assertThat(generatedFile.fileName).describedAs("Should generate Kotlin file").endsWith(".kt")
        assertThat(generatedFile.content).describedAs("Should contain Kotlin object declaration").contains("object")
        assertThat(generatedFile.content).describedAs("Should carry the node-centric FlowNodes").contains("object FlowNodes").doesNotContain("object Elements")
        assertThat(generatedFile.content).describedAs("Should contain process ID").contains("bikeLeasing")
        assertThat(generatedFile.processId).describedAs("Should carry the process id").isEqualTo("bikeLeasing")

        // and: the shared definitions are separate files that belong to no process
        val serviceTasksFile = response.files.single { it.fileName == "ServiceTasks.kt" }
        assertThat(serviceTasksFile.processId).isNull()
        assertThat(serviceTasksFile.content).contains("miravelo.sendContract")

        // and: the bundled runtime sources and dependency snippet ride along with the response
        assertThat(response.libraryFiles).describedAs("Should bundle runtime library sources").isNotEmpty()
        assertThat(response.runtimeDependency).describedAs("Should include the runtime dependency").isNotNull()
    }

    @Test
    fun `should generate C# API without the jvm runtime attachments`() {
        // given: a valid Zeebe BPMN file with C# output language
        val request = GenerateRequest(
            files = listOf(
                BpmnFileData(
                    fileName = "zeebe-bike-leasing.bpmn",
                    content = loadBpmnBase64("bpmn/zeebe/bike-leasing.bpmn"),
                ),
            ),
            config = GenerateRequest.GenerationConfig(
                outputLanguage = OutputLanguage.CSHARP,
                processEngine = ProcessEngine.ZEEBE,
            ),
        )

        // when: generating the API
        val response = underTest.generate(request)

        // then: a C# file is generated
        assertThat(response.success).isTrue()
        val generatedFile = response.files.first()
        assertThat(generatedFile.fileName).describedAs("Should generate C# file").endsWith(".cs")
        assertThat(generatedFile.content).contains("public static class", "bikeLeasing")
        assertThat(generatedFile.content).describedAs("Should carry FlowNodes with inlined runtime types").contains("public static class FlowNodes", "public static class Runtime")

        // and: no JVM runtime is offered, because the generated C# inlines its own runtime types
        assertThat(response.libraryFiles).describedAs("Should not bundle jvm runtime sources").isEmpty()
        assertThat(response.runtimeDependency).describedAs("Should not offer a jvm dependency").isNull()
    }

    @Test
    fun `should generate Java API from BPMN file`() {
        // given: a valid Zeebe BPMN file with Java output language
        val request = GenerateRequest(
            files = listOf(
                BpmnFileData(
                    fileName = "zeebe-bike-leasing.bpmn",
                    content = loadBpmnBase64("bpmn/zeebe/bike-leasing.bpmn"),
                ),
            ),
            config = GenerateRequest.GenerationConfig(
                outputLanguage = OutputLanguage.JAVA,
                processEngine = ProcessEngine.ZEEBE,
            ),
        )

        // when: generating the API
        val response = underTest.generate(request)

        // then: a Java class file is generated
        assertThat(response.success).isTrue()
        assertThat(response.files).isNotEmpty()
        val generatedFile = response.files.first()
        assertThat(generatedFile.fileName).describedAs("Should generate Java file").endsWith(".java")
        assertThat(generatedFile.content).describedAs("Should contain Java class declaration").contains("class")
    }

    @Test
    fun `should reject a model whose target engine does not match the selected engine`() {
        // given: a Zeebe model but Camunda 7 selected (the demo's original failure mode)
        val request = GenerateRequest(
            files = listOf(
                BpmnFileData(
                    fileName = "zeebe-bike-leasing.bpmn",
                    content = loadBpmnBase64("bpmn/zeebe/bike-leasing.bpmn"),
                ),
            ),
            config = GenerateRequest.GenerationConfig(
                outputLanguage = OutputLanguage.KOTLIN,
                processEngine = ProcessEngine.CAMUNDA_7,
            ),
        )

        // when: generating the API
        val response = underTest.generate(request)

        // then: the engine-mismatch error is reported (collected alongside any other problems)
        assertThat(response.success).isFalse()
        assertThat(response.files).isEmpty()
        assertThat(response.error)
            .contains("engine-mismatch", "targets Zeebe (Camunda 8)", "selected engine is Camunda 7")
    }

    @Test
    fun `should reject a Camunda 7 model when Operaton is selected`() {
        // given: a Camunda 7 model but Operaton selected (the reported case)
        val request = GenerateRequest(
            files = listOf(
                BpmnFileData(
                    fileName = "c7-bike-leasing.bpmn",
                    content = loadBpmnBase64("bpmn/c7/bike-leasing.bpmn"),
                ),
            ),
            config = GenerateRequest.GenerationConfig(
                outputLanguage = OutputLanguage.KOTLIN,
                processEngine = ProcessEngine.OPERATON,
            ),
        )

        // when: generating the API
        val response = underTest.generate(request)

        // then: surfaced as an engine-mismatch error, not a swallowed warning
        assertThat(response.success).isFalse()
        assertThat(response.error).contains("targets Camunda 7", "selected engine is Operaton")
    }

    @Test
    fun `should handle invalid Base64 content gracefully`() {
        // given: a request with invalid Base64 content
        val request = GenerateRequest(
            files = listOf(BpmnFileData(fileName = "invalid.bpmn", content = "not-valid-base64!!!")),
            config = GenerateRequest.GenerationConfig(
                outputLanguage = OutputLanguage.KOTLIN,
                processEngine = ProcessEngine.ZEEBE,
            ),
        )

        // when: generating the API
        val response = underTest.generate(request)

        // then: the response indicates failure with an error message
        assertThat(response.success).isFalse()
        assertThat(response.error).isNotNull()
        assertThat(response.files).isEmpty()
    }

    @Test
    fun `should process up to 3 BPMN files successfully`() {
        // given: a request with 3 different BPMN processes
        val request = GenerateRequest(
            files = listOf(
                BpmnFileData(fileName = "bike-leasing.bpmn", content = loadBpmnBase64("bpmn/zeebe/bike-leasing.bpmn")),
                BpmnFileData(fileName = "membership.bpmn", content = loadBpmnBase64("bpmn/zeebe/membership.bpmn")),
                BpmnFileData(fileName = "welcome-package.bpmn", content = loadBpmnBase64("bpmn/zeebe/welcome-package.bpmn")),
            ),
            config = GenerateRequest.GenerationConfig(
                outputLanguage = OutputLanguage.KOTLIN,
                processEngine = ProcessEngine.ZEEBE,
            ),
        )

        // when: generating the API for all files
        val response = underTest.generate(request)

        // then: generation succeeds and produces at least one file
        assertThat(response.error).isNull()
        assertThat(response.success).describedAs("Should successfully process 3 files").isTrue()
        assertThat(response.files).describedAs("Should generate at least one API file").isNotEmpty()
    }

    @Test
    fun `should reject files sharing a process id unless variants are enabled`() {
        // given: the same process uploaded twice without enabling variants
        val c8Base64 = loadBpmnBase64("bpmn/zeebe/bike-leasing.bpmn")
        val request = GenerateRequest(
            files = listOf(
                BpmnFileData(fileName = "bike-leasing-a.bpmn", content = c8Base64),
                BpmnFileData(fileName = "bike-leasing-b.bpmn", content = c8Base64),
            ),
            config = GenerateRequest.GenerationConfig(
                outputLanguage = OutputLanguage.KOTLIN,
                processEngine = ProcessEngine.ZEEBE,
            ),
        )

        // when: generating the API
        val response = underTest.generate(request)

        // then: a bad request names both files and the opt-in flag
        assertThat(response.success).isFalse()
        assertThat(response.statusCode).isEqualTo(HttpStatusCode.BadRequest)
        assertThat(response.error).contains("bike-leasing-a, bike-leasing-b", "enableVariants")
    }

    private fun loadBpmnBase64(resourcePath: String): String {
        val bytes = javaClass.classLoader.getResourceAsStream(resourcePath)!!.readBytes()
        return Base64.getEncoder().encodeToString(bytes)
    }
}
