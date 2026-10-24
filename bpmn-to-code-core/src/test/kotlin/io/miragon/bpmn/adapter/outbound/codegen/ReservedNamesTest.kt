package io.miragon.bpmn.adapter.outbound.codegen

import io.miragon.bpmn.domain.ProcessApiNames
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReservedNamesTest {

    @Test
    fun `reserves the name of every shared definition a node refers to by its simple name`() {
        val sharedTypeNames = SharedDefinitionType.entries.map { it.typeName }

        assertThat(ProcessApiNames.RESERVED_TYPE_NAMES).containsAll(sharedTypeNames)
    }
}
