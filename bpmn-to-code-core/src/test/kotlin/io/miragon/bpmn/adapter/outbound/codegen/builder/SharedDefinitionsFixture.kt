package io.miragon.bpmn.adapter.outbound.codegen.builder

import io.miragon.bpmn.adapter.outbound.assertMatchesGolden
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.SharedDefinitionsApi
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.testBikeLeasingModel
import io.miragon.bpmn.domain.testCancelBikeOrderModel
import org.assertj.core.api.Assertions.assertThat
import java.io.File

/**
 * Neither mirrored process uses a signal, so the one of the membership process covers that kind.
 */
private val membershipSignal = RootElementDefinition.Signal(
    id = "signal_memberActivated",
    name = "miravelo.memberActivated",
)

internal fun miraVeloSharedDefinitionsApi(language: OutputLanguage) = SharedDefinitionsApi(
    definitions = SharedDefinitions.from(listOf(testBikeLeasingModel(), testCancelBikeOrderModel()))
        .copy(signals = listOf(membershipSignal)),
    outputLanguage = language,
    packagePath = "de.emaarco.example",
)

internal fun assertMatchesGoldenFiles(generatedFiles: List<GeneratedApiFile>, goldenDirectory: String) {
    generatedFiles.forEach { file ->
        val goldenResource = "$goldenDirectory/${file.fileName.substringBeforeLast('.')}.txt"
        assertMatchesGolden(file.content, goldenResource)
    }
    val goldenNames = File("src/test/resources$goldenDirectory").list().orEmpty().map { it.removeSuffix(".txt") }
    assertThat(generatedFiles.map { it.fileName.substringBeforeLast('.') }).containsExactlyInAnyOrderElementsOf(goldenNames)
}
