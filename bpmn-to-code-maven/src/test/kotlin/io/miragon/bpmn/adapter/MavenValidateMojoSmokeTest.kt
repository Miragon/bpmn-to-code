package io.miragon.bpmn.adapter

import org.apache.maven.plugin.MojoFailureException
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File

class MavenValidateMojoSmokeTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        "ZEEBE, zeebe/bike-leasing.bpmn",
        "CAMUNDA_7, c7/bike-leasing.bpmn",
        "OPERATON, operaton/bike-leasing.bpmn",
    )
    fun `mojo validates BPMN files without errors`(engine: String, bpmnFile: String, @TempDir projectDir: File) {
        // given: a temp project directory with a valid BPMN resource and a configured mojo
        val resourcesDir = File(projectDir, "src/main/resources").also { it.mkdirs() }
        val bpmnStream = javaClass.classLoader.getResourceAsStream("bpmn/$bpmnFile")!!
        File(resourcesDir, File(bpmnFile).name).writeBytes(bpmnStream.readBytes())
        val mojo = BpmnValidateMojo()
        setField(obj = mojo, name = "baseDir", value = projectDir.absolutePath)
        setField(obj = mojo, name = "filePattern", value = "src/main/resources/*.bpmn")
        setField(obj = mojo, name = "processEngine", value = engine)
        setField(obj = mojo, name = "failOnWarning", value = false)

        // when / then: executing the mojo does not throw
        assertThatCode { mojo.execute() }.doesNotThrowAnyException()
    }

    @Test
    fun `mojo fails the build with the findings for models of another engine`(@TempDir projectDir: File) {
        // given: a Camunda 7 model validated for Zeebe
        val resourcesDir = File(projectDir, "src/main/resources").also { it.mkdirs() }
        val bpmnStream = javaClass.classLoader.getResourceAsStream("bpmn/c7/bike-leasing.bpmn")!!
        File(resourcesDir, "bike-leasing.bpmn").writeBytes(bpmnStream.readBytes())
        val mojo = BpmnValidateMojo()
        setField(obj = mojo, name = "baseDir", value = projectDir.absolutePath)
        setField(obj = mojo, name = "filePattern", value = "src/main/resources/*.bpmn")
        setField(obj = mojo, name = "processEngine", value = "ZEEBE")
        setField(obj = mojo, name = "failOnWarning", value = false)

        // when / then: the build fails with the summary and the rule that reported
        assertThatThrownBy { mojo.execute() }
            .isInstanceOf(MojoFailureException::class.java)
            .hasMessageContaining("BPMN validation failed")
            .hasMessageContaining("(rule: engine-mismatch)")
    }
}
