package io.miragon.bpmn.adapter.inbound

import io.miragon.bpmn.application.port.inbound.LocateBpmnFilesQuery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

class LocateBpmnFilesPluginTest {

    private val query = mockk<LocateBpmnFilesQuery>(relaxed = true)

    private val underTest = LocateBpmnFilesPlugin(query = query)

    @Test
    fun `resolveSearchDirectory delegates to the query with the criteria`() {
        // given: a query that resolves the search directory
        val resources = Path.of("/project/src/main/resources")
        every { query.resolveSearchDirectory(criteria) } returns resources

        // when: asking for the search directory
        val actual = underTest.resolveSearchDirectory(baseDir = "/project", filePattern = "src/main/resources/*.bpmn")

        // then: the query's answer is returned
        assertThat(actual).isEqualTo(resources)
        verify { query.resolveSearchDirectory(criteria) }
        confirmVerified(query)
    }

    @Test
    fun `isBpmnFileToLoad delegates to the query with the criteria`() {
        // given: a query that accepts the file
        every { query.isBpmnFileToLoad(criteria, "order.bpmn") } returns true

        // when: asking whether the file would be loaded
        val actual = underTest.isBpmnFileToLoad(
            baseDir = "/project",
            filePattern = "src/main/resources/*.bpmn",
            pathInSearchDirectory = "order.bpmn",
        )

        // then: the query's answer is returned
        assertThat(actual).isTrue()
        verify { query.isBpmnFileToLoad(criteria, "order.bpmn") }
        confirmVerified(query)
    }

    private val criteria = LocateBpmnFilesQuery.Criteria(baseDir = "/project", filePattern = "src/main/resources/*.bpmn")
}
