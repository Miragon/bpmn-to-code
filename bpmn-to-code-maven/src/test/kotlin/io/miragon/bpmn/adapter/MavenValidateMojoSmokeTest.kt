package io.miragon.bpmn.adapter

import org.assertj.core.api.Assertions.assertThatCode
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

    private fun setField(obj: Any, name: String, value: Any) {
        val field = obj.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(obj, value)
    }
}
