package io.miragon.bpmn.adapter.outbound

import org.assertj.core.api.Assertions.assertThat
import java.io.File

/**
 * Asserts that [generated] equals the golden file [goldenResource] byte for byte. Run with `-Dgolden.update=true` to
 * rewrite the golden file from [generated] instead.
 */
internal fun assertMatchesGolden(generated: String, goldenResource: String) {
    if (System.getProperty("golden.update") == "true") {
        File("src/test/resources$goldenResource").apply { parentFile.mkdirs() }.writeText(generated)
    } else {
        val golden = requireNotNull(object {}.javaClass.getResource(goldenResource)) { "missing golden file $goldenResource" }
        assertThat(generated).isEqualTo(golden.readText())
    }
}
