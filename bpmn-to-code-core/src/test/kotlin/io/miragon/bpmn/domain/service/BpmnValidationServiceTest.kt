package io.miragon.bpmn.domain.service

import io.miragon.bpmn.domain.DuplicateProcessIdException
import io.miragon.bpmn.domain.SourcedProcessModel
import io.miragon.bpmn.domain.jobWorkerTask
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.shared.TaskImplementation
import io.miragon.bpmn.domain.shared.TaskKind
import io.miragon.bpmn.domain.testProcessModel
import io.miragon.bpmn.domain.validation.BpmnValidationException
import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationConfig
import io.miragon.bpmn.domain.validation.model.ValidationPhase
import io.miragon.bpmn.domain.validation.model.ValidationViolation
import io.miragon.bpmn.domain.validation.rules.EmptyProcessRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class BpmnValidationServiceTest {

    private val underTest = BpmnValidationService()

    private fun serviceTaskWithoutImplementation(id: String) = FlowNodeDefinition.Activity.Task(
        id = id,
        kind = TaskKind.SERVICE,
        implementation = TaskImplementation.Unspecified,
    )

    @Test
    fun `valid model passes all pre-merge rules`() {
        // given: a valid BPMN model whose detected engine matches the selected one
        val model = testProcessModel(detectedEngine = ProcessEngine.ZEEBE)

        // when / then: no exception is thrown
        assertDoesNotThrow {
            underTest.validate(models = listOf(model), engine = ProcessEngine.ZEEBE, phase = ValidationPhase.PRE_MERGE)
        }
    }

    @Test
    fun `throws BpmnValidationException for missing service task implementation`() {
        // given: a model with a service task that has no implementation
        val model = testProcessModel(flowNodes = listOf(serviceTaskWithoutImplementation("task1")))

        // when: validating pre-merge
        val exception = assertThrows<BpmnValidationException> {
            underTest.validate(models = listOf(model), engine = ProcessEngine.ZEEBE, phase = ValidationPhase.PRE_MERGE)
        }

        // then: the missing-implementation rule fires
        assertThat(exception.violations).anyMatch { it.ruleId == "missing-service-task-implementation" }
    }

    @Test
    fun `disabled rule is skipped during validation`() {
        // given: a service with the implementation rule disabled and a model that would violate it
        val underTest = BpmnValidationService(
            ValidationConfig(disabledRules = setOf("missing-service-task-implementation")),
        )
        val model = testProcessModel(flowNodes = listOf(serviceTaskWithoutImplementation("task1")))

        // when / then: no exception is thrown because the rule is disabled
        assertDoesNotThrow {
            underTest.validate(models = listOf(model), engine = ProcessEngine.ZEEBE, phase = ValidationPhase.PRE_MERGE)
        }
    }

    @Test
    fun `warnings do not throw by default`() {
        // given: a model that produces only warnings (empty process)
        val model = testProcessModel(flowNodes = emptyList())

        // when / then: no exception is thrown
        assertDoesNotThrow {
            underTest.validate(models = listOf(model), engine = ProcessEngine.ZEEBE, phase = ValidationPhase.PRE_MERGE)
        }
    }

    @Test
    fun `failOnWarning promotes warnings to failures`() {
        // given: a service with failOnWarning and a model with an empty process
        val underTest = BpmnValidationService(ValidationConfig(failOnWarning = true))
        val model = testProcessModel(flowNodes = emptyList())

        // when: validating pre-merge
        val exception = assertThrows<BpmnValidationException> {
            underTest.validate(models = listOf(model), engine = ProcessEngine.ZEEBE, phase = ValidationPhase.PRE_MERGE)
        }

        // then: the empty-process warning is treated as a failure
        assertThat(exception.violations).anyMatch { it.ruleId == "empty-process" }
    }

    @Test
    fun `throws BpmnValidationException for flow node with null element id`() {
        // given: a model containing a flow node without an ID
        val model = testProcessModel(flowNodes = listOf(FlowNodeDefinition.Unknown(id = null)))

        // when: validating pre-merge
        val exception = assertThrows<BpmnValidationException> {
            underTest.validate(models = listOf(model), engine = ProcessEngine.ZEEBE, phase = ValidationPhase.PRE_MERGE)
        }

        // then: the missing-element-id rule fires with ERROR severity
        assertThat(exception.violations).anyMatch {
            it.ruleId == "missing-element-id" && it.severity == Severity.ERROR
        }
    }

    @Test
    fun `post-merge collision detection detects collisions`() {
        // given: a model with two flow nodes that produce the same constant name
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "endEvent_complete"),
                FlowNodeDefinition.Unknown(id = "endEvent-complete"),
            ),
        )

        // when: validating post-merge
        val exception = assertThrows<BpmnValidationException> {
            underTest.validate(models = listOf(model), engine = ProcessEngine.ZEEBE, phase = ValidationPhase.POST_MERGE)
        }

        // then: the collision-detection rule fires
        assertThat(exception.violations).anyMatch { it.ruleId == "collision-detection" }
    }

    @Test
    fun `post-merge collision detection detects folding collisions`() {
        // given: two flow nodes whose ids keep distinct constants but fold to the same
        // PascalCase object name — previously emitted non-compiling generated code
        val model = testProcessModel(
            flowNodes = listOf(FlowNodeDefinition.Unknown(id = "foo"), FlowNodeDefinition.Unknown(id = "-foo")),
        )

        // when: validating post-merge
        val exception = assertThrows<BpmnValidationException> {
            underTest.validate(models = listOf(model), engine = ProcessEngine.ZEEBE, phase = ValidationPhase.POST_MERGE)
        }

        // then: the collision-detection rule fires
        assertThat(exception.violations).anyMatch { it.ruleId == "collision-detection" }
    }

    @Test
    fun `post-merge validation detects shared definition collisions across processes`() {
        // given: two processes whose job types normalize to the same constant
        val first = testProcessModel(processId = "first", flowNodes = listOf(jobWorkerTask(id = "task1", jobType = "newsletter.sendMail")))
        val second = testProcessModel(processId = "second", flowNodes = listOf(jobWorkerTask(id = "task2", jobType = "newsletter-sendMail")))

        // when: validating post-merge
        val exception = assertThrows<BpmnValidationException> {
            underTest.validate(
                models = listOf(first, second),
                engine = ProcessEngine.ZEEBE,
                phase = ValidationPhase.POST_MERGE,
            )
        }

        // then: the shared-definition-collision rule fires
        assertThat(exception.violations).anyMatch { it.ruleId == "shared-definition-collision" }
    }

    @Test
    fun `mandatory shared-definition-collision rule stays active even when disabled`() {
        // given: a service that tries to disable the mandatory shared-definition-collision rule
        val underTest = BpmnValidationService(ValidationConfig(disabledRules = setOf("shared-definition-collision")))
        val first = testProcessModel(processId = "first", flowNodes = listOf(jobWorkerTask(id = "task1", jobType = "newsletter.sendMail")))
        val second = testProcessModel(processId = "second", flowNodes = listOf(jobWorkerTask(id = "task2", jobType = "newsletter-sendMail")))

        // when: validating post-merge
        val exception = assertThrows<BpmnValidationException> {
            underTest.validate(
                models = listOf(first, second),
                engine = ProcessEngine.ZEEBE,
                phase = ValidationPhase.POST_MERGE,
            )
        }

        // then: the rule still fires despite being disabled
        assertThat(exception.violations).anyMatch { it.ruleId == "shared-definition-collision" }
    }

    @Test
    fun `mandatory collision-detection rule stays active even when disabled`() {
        // given: a service that tries to disable the mandatory collision-detection rule
        val underTest = BpmnValidationService(ValidationConfig(disabledRules = setOf("collision-detection")))
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "endEvent_complete"),
                FlowNodeDefinition.Unknown(id = "endEvent-complete"),
            ),
        )

        // when: validating post-merge
        val exception = assertThrows<BpmnValidationException> {
            underTest.validate(models = listOf(model), engine = ProcessEngine.ZEEBE, phase = ValidationPhase.POST_MERGE)
        }

        // then: the collision-detection rule still fires despite being disabled
        assertThat(exception.violations).anyMatch { it.ruleId == "collision-detection" }
    }

    @Test
    fun `mandatory missing-element-id rule stays active even when disabled`() {
        // given: a service that tries to disable the mandatory missing-element-id rule
        val underTest = BpmnValidationService(ValidationConfig(disabledRules = setOf("missing-element-id")))
        val model = testProcessModel(flowNodes = listOf(FlowNodeDefinition.Unknown(id = null)))

        // when: validating pre-merge
        val exception = assertThrows<BpmnValidationException> {
            underTest.validate(models = listOf(model), engine = ProcessEngine.ZEEBE, phase = ValidationPhase.PRE_MERGE)
        }

        // then: the missing-element-id rule still fires despite being disabled
        assertThat(exception.violations).anyMatch { it.ruleId == "missing-element-id" }
    }

    @Test
    fun `validates with the given rules instead of the built-in ones`() {
        // given: a service that only knows the empty-process rule
        val underTest = BpmnValidationService(rules = listOf(EmptyProcessRule()))
        val emptyModel = testProcessModel(processId = "empty", flowNodes = emptyList())
        val unimplementedModel = testProcessModel(
            processId = "unimplemented",
            flowNodes = listOf(serviceTaskWithoutImplementation("Task_1")),
        )

        // when
        val violations = underTest.collectViolations(
            models = listOf(emptyModel, unimplementedModel),
            engine = ProcessEngine.ZEEBE,
            phase = ValidationPhase.PRE_MERGE,
        )

        // then: only the given rule reports, the built-in service-task check does not run
        assertThat(violations.map { it.ruleId to it.processId }).containsExactly("empty-process" to "empty")
    }

    @Test
    fun `validateAndMerge merges files sharing a process id into variants when enabled`() {
        // given: two variants of one process
        val sources = listOf(
            SourcedProcessModel("v1.bpmn", testProcessModel(variantName = "v1")),
            SourcedProcessModel("v2.bpmn", testProcessModel(variantName = "v2")),
        )

        // when: validating and merging with variants enabled
        val mergedModels = underTest.validateAndMerge(sources = sources, engine = ProcessEngine.ZEEBE, enableVariants = true)

        // then: one model carries both variants
        assertThat(mergedModels).hasSize(1)
        assertThat(mergedModels.single().variants.map { it.variantName }).containsExactly("v1", "v2")
    }

    @Test
    fun `validateAndMerge rejects a process id declared in several files unless variants are enabled`() {
        val sources = listOf(
            SourcedProcessModel("v1.bpmn", testProcessModel(variantName = "v1")),
            SourcedProcessModel("v2.bpmn", testProcessModel(variantName = "v2")),
        )

        assertThrows<DuplicateProcessIdException> {
            underTest.validateAndMerge(sources = sources, engine = ProcessEngine.ZEEBE, enableVariants = false)
        }
    }

    @Test
    fun `validateAndMerge validates each file before rejecting a shared process id`() {
        // given: two files of one process, one of them with a service task lacking an implementation
        val sources = listOf(
            SourcedProcessModel("v1.bpmn", testProcessModel(flowNodes = listOf(serviceTaskWithoutImplementation("task1")))),
            SourcedProcessModel("v2.bpmn", testProcessModel()),
        )

        // when: validating and merging with variants disabled
        val exception = assertThrows<BpmnValidationException> {
            underTest.validateAndMerge(sources = sources, engine = ProcessEngine.ZEEBE, enableVariants = false)
        }

        // then: the pre-merge violation is reported, not the shared process id
        assertThat(exception.violations).anyMatch { it.ruleId == "missing-service-task-implementation" }
    }

    @Test
    fun `validateAndMerge runs the post-merge rules on the merged models`() {
        // given: a post-merge rule that only a merged model with variants violates
        val underTest = BpmnValidationService(rules = listOf(RejectsVariantsRule()))
        val sources = listOf(
            SourcedProcessModel("v1.bpmn", testProcessModel(variantName = "v1")),
            SourcedProcessModel("v2.bpmn", testProcessModel(variantName = "v2")),
        )

        // when: validating and merging with variants enabled
        val exception = assertThrows<BpmnValidationException> {
            underTest.validateAndMerge(sources = sources, engine = ProcessEngine.ZEEBE, enableVariants = true)
        }

        // then: the rule saw the merged model
        assertThat(exception.violations).extracting("ruleId").containsExactly("rejects-variants")
    }

    private class RejectsVariantsRule : SingleModelValidationRule {
        override val id = "rejects-variants"
        override val severity = Severity.ERROR
        override val phase = ValidationPhase.POST_MERGE

        override fun validate(context: SingleModelValidationContext): List<ValidationViolation> {
            if (context.model.variants.isEmpty()) return emptyList()
            return listOf(violation(processId = context.model.processId, message = "Variants are not allowed."))
        }
    }
}
