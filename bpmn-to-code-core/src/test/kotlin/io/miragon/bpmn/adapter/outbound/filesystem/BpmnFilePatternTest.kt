package io.miragon.bpmn.adapter.outbound.filesystem

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

class BpmnFilePatternTest {

    private val baseDirectory = Path.of("project").toAbsolutePath()

    @Test
    fun `a pattern without a directory matches only files directly in the base directory`() {
        val underTest = BpmnFilePattern.of(baseDirectory = baseDirectory.toString(), filePattern = "*.bpmn")

        assertThat(underTest.searchDirectory).isEqualTo(baseDirectory)
        assertThat(underTest.matches(baseDirectory.resolve("order.bpmn"))).isTrue()
        assertThat(underTest.matches(baseDirectory.resolve("nested/order.bpmn"))).isFalse()
        assertThat(underTest.matches(baseDirectory.resolve("order.txt"))).isFalse()
    }

    @Test
    fun `a leading recursive wildcard also matches files directly in the base directory`() {
        val underTest = BpmnFilePattern.of(baseDirectory = baseDirectory.toString(), filePattern = "**/*.bpmn")

        assertThat(underTest.searchDirectory).isEqualTo(baseDirectory)
        assertThat(underTest.matches(baseDirectory.resolve("order.bpmn"))).isTrue()
        assertThat(underTest.matches(baseDirectory.resolve("a/b/order.bpmn"))).isTrue()
    }

    @Test
    fun `the directories in front of the first wildcard narrow the search directory`() {
        val underTest = BpmnFilePattern.of(
            baseDirectory = baseDirectory.toString(),
            filePattern = "src/main/resources/**/*.bpmn",
        )

        // then: the search starts below the fixed directories and matches from there
        val resources = baseDirectory.resolve("src/main/resources")
        assertThat(underTest.searchDirectory).isEqualTo(resources)
        assertThat(underTest.matches(resources.resolve("order.bpmn"))).isTrue()
        assertThat(underTest.matches(resources.resolve("c8/order.bpmn"))).isTrue()
        assertThat(underTest.matches(resources.resolve("c8/order.txt"))).isFalse()
    }

    @Test
    fun `a pattern without a wildcard matches exactly that file`() {
        val underTest = BpmnFilePattern.of(baseDirectory = baseDirectory.toString(), filePattern = "../external/order.bpmn")

        // then: the search directory leaves the base directory and only the named file matches
        val external = baseDirectory.resolveSibling("external")
        assertThat(underTest.searchDirectory).isEqualTo(external)
        assertThat(underTest.matches(external.resolve("order.bpmn"))).isTrue()
        assertThat(underTest.matches(external.resolve("payment.bpmn"))).isFalse()
        assertThat(underTest.matches(external.resolve("nested/order.bpmn"))).isFalse()
    }
}
