package io.miragon.bpmn.adapter.outbound.codegen.builder

import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.SharedDefinitionsApi
import io.miragon.bpmn.domain.service.SharedDefinitionsService
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.testBikeLeasingModel
import io.miragon.bpmn.domain.testCancelBikeOrderModel
import java.io.File

internal fun miraVeloSharedDefinitionsApi(language: OutputLanguage) = SharedDefinitionsApi(
    definitions = SharedDefinitionsService().collect(listOf(testBikeLeasingModel(), testCancelBikeOrderModel())),
    outputLanguage = language,
    packagePath = "de.emaarco.example",
)

internal fun List<GeneratedApiFile>.asGoldenText(): String = joinToString("\n") { "// ===== ${it.fileName}\n${it.content}" }

internal fun readGolden(resource: String, generated: String): String {
    if (System.getProperty("golden.update") == "true") {
        File("src/test/resources$resource").writeText(generated)
        return generated
    }
    return File(requireNotNull(object {}.javaClass.getResource(resource)).toURI()).readText()
}
