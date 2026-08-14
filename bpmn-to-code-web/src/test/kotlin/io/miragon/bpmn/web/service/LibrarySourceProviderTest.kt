package io.miragon.bpmn.web.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LibrarySourceProviderTest {

    private val underTest = LibrarySourceProvider()

    @Test
    fun `libraryFiles exposes the bundled runtime Kotlin sources`() {
        val files = underTest.libraryFiles()

        assertThat(files).isNotEmpty()
        assertThat(files).allSatisfy {
            assertThat(it.fileName).endsWith(".kt")
            assertThat(it.content).isNotBlank()
            assertThat(it.processId).isEqualTo("bpmn-to-code-runtime")
        }
    }

    @Test
    fun `libraryFiles are loaded once and cached`() {
        assertThat(underTest.libraryFiles()).isSameAs(underTest.libraryFiles())
    }

    @Test
    fun `runtimeDependency describes the published runtime artifact with a resolved version`() {
        val dependency = underTest.runtimeDependency()

        assertThat(dependency.group).isEqualTo("io.miragon")
        assertThat(dependency.artifact).isEqualTo("bpmn-to-code-runtime")
        assertThat(dependency.version).isNotEqualTo("unknown")
        assertThat(dependency.version).matches("\\d+\\..*")
        assertThat(dependency.gradleSnippet)
            .isEqualTo("implementation(\"io.miragon:bpmn-to-code-runtime:${dependency.version}\")")
        assertThat(dependency.mavenSnippet)
            .contains("<groupId>io.miragon</groupId>")
            .contains("<artifactId>bpmn-to-code-runtime</artifactId>")
            .contains("<version>${dependency.version}</version>")
    }
}
