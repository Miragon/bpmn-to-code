package io.miragon.bpmn.web.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class LibrarySourceProviderTest {

    private val underTest = LibrarySourceProvider()

    @ParameterizedTest
    @CsvSource("JVM, .kt", "CSHARP, .cs", "TYPESCRIPT, .ts")
    fun `libraryFiles exposes the bundled sources of each runtime library`(library: RuntimeLibrary, extension: String) {
        val files = underTest.libraryFiles(library)

        assertThat(files).isNotEmpty()
        assertThat(files).allSatisfy {
            assertThat(it.fileName).endsWith(extension)
            assertThat(it.content).isNotBlank()
            assertThat(it.processId).isEqualTo("bpmn-to-code-runtime")
        }
    }

    @Test
    fun `libraryFiles of the C# and TypeScript runtimes include ProcessPath`() {
        assertThat(underTest.libraryFiles(RuntimeLibrary.CSHARP).map { it.fileName }).contains("Path/ProcessPath.cs")
        assertThat(underTest.libraryFiles(RuntimeLibrary.TYPESCRIPT).map { it.fileName }).contains("process-path.ts")
    }

    @Test
    fun `libraryFiles are loaded once and cached`() {
        assertThat(underTest.libraryFiles(RuntimeLibrary.JVM)).isSameAs(underTest.libraryFiles(RuntimeLibrary.JVM))
    }

    @Test
    fun `runtimeDependency describes the published JVM artifact with a resolved version`() {
        val dependency = underTest.runtimeDependency(RuntimeLibrary.JVM)

        assertThat(dependency.name).isEqualTo("io.miragon:bpmn-to-code-runtime")
        assertThat(dependency.importedFrom).isEqualTo("io.miragon.bpmn.runtime")
        assertThat(dependency.version).isNotEqualTo("unknown").matches("\\d+\\..*")
        assertThat(dependency.snippets.map { it.label }).containsExactly("Gradle", "Maven")
        assertThat(dependency.snippets.first().code).isEqualTo("implementation(\"io.miragon:bpmn-to-code-runtime:${dependency.version}\")")
        assertThat(dependency.snippets.last().code)
            .contains("<groupId>io.miragon</groupId>", "<artifactId>bpmn-to-code-runtime</artifactId>", "<version>${dependency.version}</version>")
    }

    @Test
    fun `runtimeDependency names the NuGet and npm packages in the same version`() {
        val csharp = underTest.runtimeDependency(RuntimeLibrary.CSHARP)
        val typescript = underTest.runtimeDependency(RuntimeLibrary.TYPESCRIPT)

        assertThat(csharp.snippets.map { it.code }).contains("dotnet add package Miragon.BpmnToCode.Runtime --version ${csharp.version}")
        assertThat(typescript.snippets.map { it.code }).contains("npm install @miragon/bpmn-to-code-runtime@${typescript.version}")
        assertThat(typescript.importedFrom).isEqualTo("@miragon/bpmn-to-code-runtime")
    }
}
