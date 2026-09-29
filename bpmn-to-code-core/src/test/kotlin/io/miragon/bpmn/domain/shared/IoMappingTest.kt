package io.miragon.bpmn.domain.shared

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IoMappingTest {

    @Test
    fun `turns each parameter target into a variable of its direction`() {
        val ioMapping = IoMapping(
            inputs = listOf(IoMapping.Parameter(target = "orderId", source = "=order.id")),
            outputs = listOf(IoMapping.Parameter(target = "approved")),
        )

        assertThat(ioMapping.toVariables()).containsExactly(
            VariableDefinition(name = "orderId", direction = VariableDirection.INPUT, valueExpression = "=order.id"),
            VariableDefinition(name = "approved", direction = VariableDirection.OUTPUT),
        )
    }
}
