package io.miragon.bpmn.adapter.outbound.codegen

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File

/**
 * Keeps the runtime libraries' checked-in example APIs in lockstep with the codegen goldens.
 *
 * `bpmn-to-code-runtime/src/test` compiles a copy of the generated bike-leasing API in Kotlin and Java, the C#
 * runtime's tests one in C#, and they run the `ProcessPath` tests over it.
 * The runtimes cannot depend on core, so this test lives here and compares the goldens to the copies,
 * ignoring only the `package` or `namespace` line.
 *
 * Regenerate the copies with `-Dgolden.update=true` after the goldens changed.
 */
class RuntimeExampleDriftTest {

    private val runtimeTestSources = File(System.getProperty("runtime.test.sources"))
    private val csharpRuntimeTests = File(System.getProperty("csharp.runtime.tests"))

    @ParameterizedTest
    @CsvSource(
        "/api/BikeLeasingProcessApiKotlin.txt, kotlin/io/miragon/bpmn/runtime/path/example/BikeLeasingProcessApi.kt, package io.miragon.bpmn.runtime.path.example",
        "/api/BikeLeasingProcessApiJava.txt, java/io/miragon/bpmn/runtime/example/BikeLeasingProcessApi.java, package io.miragon.bpmn.runtime.example;",
    )
    fun `runtime example api equals the golden apart from its package`(golden: String, example: String, packageLine: String) {
        val expected = readResource(golden).replaceFirst(Regex("^package .*$", RegexOption.MULTILINE), packageLine)

        assertExampleEquals(runtimeTestSources.resolve(example), expected)
    }

    @ParameterizedTest
    @CsvSource(
        "/api/BikeLeasingProcessApiCsharp.txt, BikeLeasingProcessApi.cs",
        "/api/shared-definitions/csharp/Errors.txt, Errors.cs",
        "/api/shared-definitions/csharp/Escalations.txt, Escalations.cs",
        "/api/shared-definitions/csharp/Messages.txt, Messages.cs",
        "/api/shared-definitions/csharp/ProcessVariables.txt, ProcessVariables.cs",
        "/api/shared-definitions/csharp/ServiceTasks.txt, ServiceTasks.cs",
        "/api/shared-definitions/csharp/Signals.txt, Signals.cs",
    )
    fun `csharp runtime example equals the golden apart from its namespace`(golden: String, example: String) {
        val expected = readResource(golden).replaceFirst(Regex("^namespace .*;$", RegexOption.MULTILINE), "namespace $CSHARP_EXAMPLE_NAMESPACE;")

        assertExampleEquals(csharpRuntimeTests.resolve("Example/$example"), expected)
    }

    private fun assertExampleEquals(exampleFile: File, expected: String) {
        if (System.getProperty("golden.update") == "true") {
            exampleFile.parentFile.mkdirs()
            exampleFile.writeText(expected)
        }

        assertThat(exampleFile.readText()).isEqualTo(expected)
    }

    private fun readResource(path: String): String = requireNotNull(javaClass.getResourceAsStream(path)) { "missing resource $path" }
        .bufferedReader().readText()

    private companion object {
        const val CSHARP_EXAMPLE_NAMESPACE = "Miragon.BpmnToCode.Runtime.Tests.Example"
    }
}
