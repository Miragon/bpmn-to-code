package io.miragon.bpmn.adapter.outbound.codegen.builder

import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.SharedDefinitionsApi
import io.miragon.bpmn.domain.service.SharedDefinitionsService
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.testSendNewsletterModel
import io.miragon.bpmn.domain.testSubscribeNewsletterModel
import java.io.File

internal fun newsletterSharedDefinitionsApi(language: OutputLanguage) = SharedDefinitionsApi(
    definitions = SharedDefinitionsService().collect(listOf(testSubscribeNewsletterModel(), testSendNewsletterModel())),
    outputLanguage = language,
    packagePath = "de.emaarco.example",
)

internal fun List<GeneratedApiFile>.asGoldenText(): String = joinToString("\n") { "// ===== ${it.fileName}\n${it.content}" }

internal fun readGolden(resource: String): String = File(requireNotNull(object {}.javaClass.getResource(resource)).toURI()).readText()
