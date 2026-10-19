package io.miragon.bpmn.adapter.outbound.codegen.surface

import io.miragon.bpmn.adapter.outbound.assertMatchesGolden
import io.miragon.bpmn.adapter.outbound.codegen.CSharpCompilationTest
import io.miragon.bpmn.application.port.inbound.GenerateProcessApiInMemoryUseCase
import io.miragon.bpmn.application.service.GenerateProcessApiInMemoryService
import io.miragon.bpmn.domain.BpmnResource
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * The generated API is a public contract in its shape, names and values, not in its formatting. This test compiles
 * the generated Kotlin and Java, reads the compiled types back by reflection and compares the result to a snapshot,
 * so a change to how the code is written shows here only when it changes what users compile against.
 *
 * Run with `-Dgolden.update=true` to rewrite the snapshots after an intended change of the API.
 */
class GeneratedApiSurfaceTest {

    private val service = GenerateProcessApiInMemoryService()
    private val values = SurfaceValues(generatedPackage = PACKAGE)

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `generated kotlin compiles and keeps its api surface`(case: SurfaceCase) {
        val generated = generate(case, OutputLanguage.KOTLIN)

        val classLoader = GeneratedSourceCompiler.compileKotlin(generated)

        val surface = KotlinApiSurface(values)
        val description = topLevelTypesOf(generated, classLoader).joinToString("\n") { surface.describe(it.kotlin) }
        assertMatchesGolden(description, "/api-surface/kotlin/${case.name}.txt")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `generated java compiles and keeps its api surface`(case: SurfaceCase) {
        val generated = generate(case, OutputLanguage.JAVA)

        val classLoader = GeneratedSourceCompiler.compileJava(generated)

        val surface = JavaApiSurface(values)
        val description = topLevelTypesOf(generated, classLoader).joinToString("\n") { surface.describe(it) }
        assertMatchesGolden(description, "/api-surface/java/${case.name}.txt")
    }

    private fun generate(case: SurfaceCase, language: OutputLanguage): List<GeneratedApiFile> {
        val command = GenerateProcessApiInMemoryUseCase.Command(
            resources = case.bpmnXmls.mapIndexed { index, bpmnXml ->
                BpmnResource(fileName = "process-$index.bpmn", content = bpmnXml.encodeToByteArray())
            },
            packagePath = PACKAGE,
            outputLanguage = language,
            engine = case.engine,
        )
        return service.generateProcessApi(command)
    }

    private fun topLevelTypesOf(generated: List<GeneratedApiFile>, classLoader: ClassLoader): List<Class<*>> {
        val typeNames = generated.map { it.fileName.substringBeforeLast('.') }.sorted()
        return typeNames.map { classLoader.loadClass("$PACKAGE.$it") }
    }

    class SurfaceCase(val name: String, val bpmnXmls: List<String>, val engine: ProcessEngine) {
        override fun toString() = name
    }

    companion object {

        private const val PACKAGE = "de.gen"

        private val MIRAVELO_MODELS = listOf("bike-leasing", "cancel-bike-order", "membership", "welcome-package")

        @JvmStatic
        fun cases(): List<SurfaceCase> = listOf(
            SurfaceCase(name = "zeebe", bpmnXmls = miraVeloModels("zeebe"), engine = ProcessEngine.ZEEBE),
            SurfaceCase(name = "c7", bpmnXmls = miraVeloModels("c7"), engine = ProcessEngine.CAMUNDA_7),
            SurfaceCase(name = "operaton", bpmnXmls = miraVeloModels("operaton"), engine = ProcessEngine.OPERATON),
            SurfaceCase(
                name = "nested-subprocess",
                bpmnXmls = listOf(resource("/bpmn/nested-subprocess.bpmn")),
                engine = ProcessEngine.ZEEBE,
            ),
            SurfaceCase(
                name = "two-flows-to-one-task",
                bpmnXmls = listOf(CSharpCompilationTest.TWO_FLOWS_TO_ONE_TASK),
                engine = ProcessEngine.ZEEBE,
            ),
        )

        private fun miraVeloModels(engineFolder: String) = MIRAVELO_MODELS.map { resource("/bpmn/$engineFolder/$it.bpmn") }

        private fun resource(path: String): String {
            val url = requireNotNull(GeneratedApiSurfaceTest::class.java.getResource(path)) { "missing resource $path" }
            return url.readText()
        }
    }
}
