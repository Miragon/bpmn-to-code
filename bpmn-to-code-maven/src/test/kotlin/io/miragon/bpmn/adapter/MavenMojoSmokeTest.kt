package io.miragon.bpmn.adapter

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File

class MavenMojoSmokeTest {

    @ParameterizedTest(name = "{0} / {1}")
    @CsvSource(
        "ZEEBE, KOTLIN, zeebe/bike-leasing.bpmn",
        "CAMUNDA_7, KOTLIN, c7/bike-leasing.bpmn",
        "OPERATON, KOTLIN, operaton/bike-leasing.bpmn",
        "ZEEBE, JAVA, zeebe/bike-leasing.bpmn",
        "ZEEBE, CSHARP, zeebe/bike-leasing.bpmn",
    )
    fun `mojo generates output files`(engine: String, language: String, bpmnFile: String, @TempDir projectDir: File) {
        // given: a temp project directory with a BPMN resource and a configured mojo
        val resourcesDir = File(projectDir, "src/main/resources").also { it.mkdirs() }
        val bpmnStream = requireNotNull(javaClass.classLoader.getResourceAsStream("bpmn/$bpmnFile"))
        File(resourcesDir, File(bpmnFile).name).writeBytes(bpmnStream.readBytes())
        val outputDir = File(projectDir, "build/generated")
        val mojo = BpmnModelMojo()
        setField(obj = mojo, name = "baseDir", value = projectDir.absolutePath)
        setField(obj = mojo, name = "filePattern", value = "src/main/resources/*.bpmn")
        setField(obj = mojo, name = "outputFolderPath", value = outputDir.absolutePath)
        setField(obj = mojo, name = "packagePath", value = "io.miragon.smoketest")
        setField(obj = mojo, name = "outputLanguage", value = language)
        setField(obj = mojo, name = "processEngine", value = engine)

        // when: executing the mojo
        mojo.execute()

        // then: only ProcessApi files are generated (shared types ship via runtime artifact)
        val packageDir = File(outputDir, "io/miragon/smoketest")
        assertThat(packageDir).isDirectory()
        val generatedFiles = requireNotNull(packageDir.listFiles())
        assertThat(generatedFiles).isNotEmpty()
        val expectedExt = when (language) {
            "KOTLIN" -> ".kt"
            "JAVA" -> ".java"
            else -> ".cs"
        }
        assertThat(generatedFiles).allSatisfy { file -> assertThat(file.isFile).isTrue() }
        assertThat(generatedFiles).allSatisfy { file -> assertThat(file.name).endsWith(expectedExt) }
    }

    @Test
    fun `mojo rejects files sharing a process id unless variants are enabled`(@TempDir projectDir: File) {
        // given: the same process copied into two files
        val resourcesDir = File(projectDir, "src/main/resources").also { it.mkdirs() }
        val bpmnBytes = requireNotNull(javaClass.classLoader.getResourceAsStream("bpmn/zeebe/bike-leasing.bpmn")).readBytes()
        File(resourcesDir, "bike-leasing-a.bpmn").writeBytes(bpmnBytes)
        File(resourcesDir, "bike-leasing-b.bpmn").writeBytes(bpmnBytes)
        val mojo = BpmnModelMojo()
        setField(obj = mojo, name = "baseDir", value = projectDir.absolutePath)
        setField(obj = mojo, name = "filePattern", value = "src/main/resources/*.bpmn")
        setField(obj = mojo, name = "outputFolderPath", value = File(projectDir, "build/generated").absolutePath)
        setField(obj = mojo, name = "packagePath", value = "io.miragon.smoketest")
        setField(obj = mojo, name = "outputLanguage", value = "KOTLIN")
        setField(obj = mojo, name = "processEngine", value = "ZEEBE")

        // when / then: the mojo fails naming both files
        assertThatThrownBy { mojo.execute() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("bike-leasing-a.bpmn, bike-leasing-b.bpmn").hasMessageContaining("enableVariants")
    }

    private fun setField(obj: Any, name: String, value: Any) {
        val field = obj.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(obj, value)
    }
}
