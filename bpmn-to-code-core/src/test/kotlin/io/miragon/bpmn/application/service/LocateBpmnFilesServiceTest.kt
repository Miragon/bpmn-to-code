package io.miragon.bpmn.application.service

import io.miragon.bpmn.application.port.inbound.LocateBpmnFilesQuery
import io.miragon.bpmn.application.port.outbound.LocateBpmnFilesPort
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

class LocateBpmnFilesServiceTest {

    private val bpmnFileLocator = mockk<LocateBpmnFilesPort>(relaxed = true)

    private val underTest = LocateBpmnFilesService(bpmnFileLocator = bpmnFileLocator)

    @Test
    fun `resolveSearchDirectory asks the file system adapter`() {
        // given: an adapter that resolves the search directory
        val resources = Path.of("/project/src/main/resources")
        every { bpmnFileLocator.resolveSearchDirectory("/project", "src/main/resources/*.bpmn") } returns resources

        // when: asking for the search directory
        val actual = underTest.resolveSearchDirectory(criteria)

        // then: the adapter's answer is returned
        assertThat(actual).isEqualTo(resources)
        verify { bpmnFileLocator.resolveSearchDirectory("/project", "src/main/resources/*.bpmn") }
        confirmVerified(bpmnFileLocator)
    }

    @Test
    fun `isBpmnFileToLoad asks the file system adapter`() {
        // given: an adapter that accepts the file
        every { bpmnFileLocator.isBpmnFileToLoad(baseDirectory = "/project", filePattern = "src/main/resources/*.bpmn", pathInSearchDirectory = "order.bpmn") } returns true

        // when: asking whether the file would be loaded
        val actual = underTest.isBpmnFileToLoad(criteria, "order.bpmn")

        // then: the adapter's answer is returned
        assertThat(actual).isTrue()
        verify { bpmnFileLocator.isBpmnFileToLoad(baseDirectory = "/project", filePattern = "src/main/resources/*.bpmn", pathInSearchDirectory = "order.bpmn") }
        confirmVerified(bpmnFileLocator)
    }

    private val criteria = LocateBpmnFilesQuery.Criteria(baseDir = "/project", filePattern = "src/main/resources/*.bpmn")
}
