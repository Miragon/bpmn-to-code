package io.miragon.bpmn.adapter.outbound.codegen

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File

/**
 * Keeps the runtime module's checked-in example APIs in lockstep with the codegen goldens.
 *
 * `bpmn-to-code-runtime/src/test` compiles a copy of the generated Newsletter API in Kotlin and Java and runs
 * the `ProcessPath` tests over it — that copy is the only place generated Kotlin is compiled against the
 * runtime. The runtime cannot depend on core, so this test lives here and compares the goldens to the copies,
 * ignoring only the `package` line.
 *
 * Regenerate the copies with `-Dgolden.update=true` after the goldens changed.
 */
class RuntimeExampleDriftTest {

    private val runtimeTestSources = File(System.getProperty("runtime.test.sources"))

    @ParameterizedTest
    @CsvSource(
        "/api/NewsletterSubscriptionProcessApiKotlin.txt, kotlin/io/miragon/bpmn/runtime/path/example/NewsletterSubscriptionProcessApi.kt, package io.miragon.bpmn.runtime.path.example",
        "/api/NewsletterSubscriptionProcessApiJava.txt, java/io/miragon/bpmn/runtime/example/NewsletterSubscriptionProcessApi.java, package io.miragon.bpmn.runtime.example;",
    )
    fun `runtime example api equals the golden apart from its package`(golden: String, example: String, packageLine: String) {
        val expected = readResource(golden).replaceFirst(Regex("^package .*$", RegexOption.MULTILINE), packageLine)
        val exampleFile = runtimeTestSources.resolve(example)

        if (System.getProperty("golden.update") == "true") exampleFile.writeText(expected)

        assertThat(exampleFile.readText()).isEqualTo(expected)
    }

    private fun readResource(path: String): String = requireNotNull(javaClass.getResourceAsStream(path)) { "missing resource $path" }
        .bufferedReader()
        .readText()
}
