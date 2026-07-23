package io.miragon.bpmn.domain.validation.rules

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Verifies which rules are integrity-critical. Mandatory rules guarantee the generated code can emit
 * a valid, unique constant name and therefore cannot be disabled via `disabledRules`.
 */
class MandatoryRuleFlagTest {

    @Test
    fun `integrity-critical rules are mandatory`() {
        assertThat(CollisionDetectionRule().mandatory).isTrue()
        assertThat(MissingElementIdRule().mandatory).isTrue()
        assertThat(MissingProcessIdRule().mandatory).isTrue()
    }

    @Test
    fun `optional rules are not mandatory`() {
        assertThat(EmptyProcessRule().mandatory).isFalse()
        assertThat(MissingServiceTaskImplementationRule().mandatory).isFalse()
    }
}
