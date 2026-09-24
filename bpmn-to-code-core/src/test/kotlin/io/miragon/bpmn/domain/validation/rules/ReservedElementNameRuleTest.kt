package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.shared.SubProcessKind
import io.miragon.bpmn.domain.testProcessModel
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationPhase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ReservedElementNameRuleTest {

    private val underTest = ReservedElementNameRule()

    @ParameterizedTest
    @ValueSource(strings = ["Flow", "next", "flows_", "element-id", "Instance", "runtime", "Messages", "hash_code", "to-string", "wait"])
    fun `reports error for an element whose generated name is reserved`(elementId: String) {
        // given: an element folding to a holder, runtime type, registry or Object method name
        val model = testProcessModel(flowNodes = listOf(FlowNodeDefinition.Unknown(id = elementId)))

        // when / then: an ERROR violation naming the element
        val violations = underTest.validate(SingleModelValidationContext(model = model, engine = ProcessEngine.ZEEBE))
        assertThat(violations).hasSize(1)
        assertThat(violations[0].severity).isEqualTo(Severity.ERROR)
        assertThat(violations[0].elementId).isEqualTo(elementId)
        assertThat(violations[0].message).contains("reserved")
    }

    @Test
    fun `also checks elements inside subprocesses, since Flow is flat`() {
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Activity.SubProcess(
                    id = "sub",
                    kind = SubProcessKind.PLAIN,
                    flowNodes = listOf(FlowNodeDefinition.Unknown(id = "start")),
                ),
            ),
        )

        val violations = underTest.validate(SingleModelValidationContext(model = model, engine = ProcessEngine.ZEEBE))

        assertThat(violations).extracting("elementId").containsExactly("start")
    }

    @Test
    fun `no violations for ordinary element ids`() {
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "startEvent_requestReceived"),
                FlowNodeDefinition.Unknown(id = "flowControl"),
                FlowNodeDefinition.Unknown(id = "nextStep"),
                FlowNodeDefinition.Unknown(id = null),
            ),
        )

        val violations = underTest.validate(SingleModelValidationContext(model = model, engine = ProcessEngine.ZEEBE))

        assertThat(violations).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["runtime", "FlowVariants", "element-id"])
    fun `reports error for a variant whose generated name is reserved`(variantName: String) {
        // given: a merged model with a variant folding to a runtime type or the wrapper itself
        val model = testProcessModel(variants = listOf(ProcessModel.Variant(variantName = variantName)))

        // when / then: an ERROR violation naming the variant
        val violations = underTest.validate(SingleModelValidationContext(model = model, engine = ProcessEngine.ZEEBE))
        assertThat(violations).hasSize(1)
        assertThat(violations[0].severity).isEqualTo(Severity.ERROR)
        assertThat(violations[0].message).contains("Variant '$variantName'")
    }

    @Test
    fun `reports error for a variant named like one of its own elements`() {
        // given: variant 'augsburg' containing an element 'augsburg'
        val variant = ProcessModel.Variant(variantName = "augsburg", flowNodes = listOf(FlowNodeDefinition.Unknown(id = "augsburg")))
        val model = testProcessModel(variants = listOf(variant))

        // when / then: the variant is reported
        val violations = underTest.validate(SingleModelValidationContext(model = model, engine = ProcessEngine.ZEEBE))
        assertThat(violations).extracting("message").allMatch { (it as String).contains("Variant 'augsburg'") }
        assertThat(violations).hasSize(1)
    }

    @Test
    fun `no violations for ordinary variant names`() {
        val variant = ProcessModel.Variant(variantName = "augsburg", flowNodes = listOf(FlowNodeDefinition.Unknown(id = "startEvent")))
        val model = testProcessModel(variants = listOf(variant, ProcessModel.Variant(variantName = "munich")))

        val violations = underTest.validate(SingleModelValidationContext(model = model, engine = ProcessEngine.ZEEBE))

        assertThat(violations).isEmpty()
    }

    @Test
    fun `is a mandatory post-merge rule`() {
        assertThat(underTest.id).isEqualTo("reserved-element-name")
        assertThat(underTest.mandatory).isTrue()
        assertThat(underTest.phase).isEqualTo(ValidationPhase.POST_MERGE)
    }
}
