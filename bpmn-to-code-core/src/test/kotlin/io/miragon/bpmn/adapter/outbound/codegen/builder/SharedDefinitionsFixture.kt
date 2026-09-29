package io.miragon.bpmn.adapter.outbound.codegen.builder

import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.SharedDefinitionsApi
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.testBikeLeasingModel
import io.miragon.bpmn.domain.testCancelBikeOrderModel
import org.assertj.core.api.Assertions.assertThat
import java.io.File

internal fun miraVeloSharedDefinitionsApi(language: OutputLanguage) = SharedDefinitionsApi(
    definitions = SharedDefinitions.from(listOf(testBikeLeasingModel(), testCancelBikeOrderModel())),
    outputLanguage = language,
    packagePath = "de.emaarco.example",
)

internal fun assertMatchesGoldenFiles(generatedFiles: List<GeneratedApiFile>, goldenDirectory: String) {
    generatedFiles.forEach { file ->
        val goldenResource = "$goldenDirectory/${file.fileName.substringBeforeLast('.')}.txt"
        assertThat(file.content).isEqualTo(readGolden(goldenResource, file.content))
    }
    val goldenNames = File("src/test/resources$goldenDirectory").list().orEmpty().map { it.removeSuffix(".txt") }
    assertThat(generatedFiles.map { it.fileName.substringBeforeLast('.') }).containsExactlyInAnyOrderElementsOf(goldenNames)
}

private fun readGolden(resource: String, generated: String): String {
    if (System.getProperty("golden.update") == "true") {
        File("src/test/resources$resource").writeText(generated)
        return generated
    }
    return File(requireNotNull(object {}.javaClass.getResource(resource)).toURI()).readText()
}
