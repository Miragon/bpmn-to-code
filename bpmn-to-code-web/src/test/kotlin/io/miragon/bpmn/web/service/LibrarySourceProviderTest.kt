package io.miragon.bpmn.web.service

import io.miragon.bpmn.domain.shared.OutputLanguage
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
    fun `runtimeDependency names the JVM runtime artifact with a Gradle and a Maven snippet`() {
        val dependency = underTest.runtimeDependency(OutputLanguage.KOTLIN)

        assertThat(dependency.name).isEqualTo("io.miragon:bpmn-to-code-runtime")
        assertThat(dependency.importedFrom).isEqualTo("io.miragon.bpmn.runtime")
        assertThat(dependency.version).isNotEqualTo("unknown").matches("\\d+\\..*")
        assertThat(dependency.snippets.map { it.label }).containsExactly("Gradle", "Maven")
        assertThat(dependency.snippets.first().code).isEqualTo("implementation(\"io.miragon:bpmn-to-code-runtime:${dependency.version}\")")
        assertThat(dependency.snippets.last().code)
            .contains("<groupId>io.miragon</groupId>", "<artifactId>bpmn-to-code-runtime</artifactId>", "<version>${dependency.version}</version>")
        assertThat(underTest.runtimeDependency(OutputLanguage.JAVA)).isEqualTo(dependency)
    }

    @Test
    fun `runtimeDependency names the NuGet runtime package for C#`() {
        val dependency = underTest.runtimeDependency(OutputLanguage.CSHARP)

        assertThat(dependency.name).isEqualTo("Miragon.BpmnToCode.Runtime")
        assertThat(dependency.importedFrom).isEqualTo("Miragon.BpmnToCode.Runtime")
        assertThat(dependency.snippets.map { it.code }).containsExactly(
            "<PackageReference Include=\"Miragon.BpmnToCode.Runtime\" Version=\"${dependency.version}\" />",
            "dotnet add package Miragon.BpmnToCode.Runtime --version ${dependency.version}",
        )
    }

    @Test
    fun `runtimeDependency names the npm runtime package for TypeScript`() {
        val dependency = underTest.runtimeDependency(OutputLanguage.TYPESCRIPT)

        assertThat(dependency.name).isEqualTo("@miragon/bpmn-to-code-runtime")
        assertThat(dependency.snippets.single().code).isEqualTo("npm install --save-exact @miragon/bpmn-to-code-runtime@${dependency.version}")
    }
}
