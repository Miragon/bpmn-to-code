package io.miragon.bpmn.domain.service

import io.miragon.bpmn.domain.ProcessApiNamingException
import io.miragon.bpmn.domain.SourcedProcessModel
import io.miragon.bpmn.domain.jobWorkerTask
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.shared.TaskImplementation
import io.miragon.bpmn.domain.shared.TaskKind
import io.miragon.bpmn.domain.testProcessModel
import io.miragon.bpmn.domain.validation.BpmnValidationException
import io.miragon.bpmn.domain.validation.CrossModelValidationRule
import io.miragon.bpmn.domain.validation.model.CrossModelValidationContext
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.ValidationConfig
import io.miragon.bpmn.domain.validation.model.ValidationViolation
import io.miragon.bpmn.domain.validation.rules.EmptyProcessRule
import io.miragon.bpmn.domain.validation.rules.MissingServiceTaskImplementationRule
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
    fun `valid model passes all rules`() {
        // given: a valid BPMN model whose detected engine matches the selected one
        val model = testProcessModel(detectedEngine = ProcessEngine.ZEEBE)

        // when / then: no exception is thrown
        assertDoesNotThrow {
            underTest.validate(models = listOf(model), engine = ProcessEngine.ZEEBE)
        }
    }

    @Test
    fun `throws BpmnValidationException for missing service task implementation`() {
        // given: a model with a service task that has no implementation
        val model = testProcessModel(flowNodes = listOf(serviceTaskWithoutImplementation("task1")))

        // when
        val exception = assertThrows<BpmnValidationException> {
            underTest.validate(models = listOf(model), engine = ProcessEngine.ZEEBE)
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
            underTest.validate(models = listOf(model), engine = ProcessEngine.ZEEBE)
        }
    }

    @Test
    fun `warnings do not throw by default`() {
        // given: a model that produces only warnings (empty process)
        val model = testProcessModel(flowNodes = emptyList())

        // when / then: no exception is thrown
        assertDoesNotThrow {
            underTest.validate(models = listOf(model), engine = ProcessEngine.ZEEBE)
        }
    }

    @Test
    fun `failOnWarning promotes warnings to failures`() {
        // given: a service with failOnWarning and a model with an empty process
        val underTest = BpmnValidationService(ValidationConfig(failOnWarning = true))
        val model = testProcessModel(flowNodes = emptyList())

        // when
        val exception = assertThrows<BpmnValidationException> {
            underTest.validate(models = listOf(model), engine = ProcessEngine.ZEEBE)
        }

        // then: the empty-process warning is treated as a failure
        assertThat(exception.violations).anyMatch { it.ruleId == "empty-process" }
    }

    @Test
    fun `throws BpmnValidationException for flow node with null element id`() {
        // given: a model containing a flow node without an ID
        val model = testProcessModel(flowNodes = listOf(FlowNodeDefinition.Unknown(id = null)))

        // when
        val exception = assertThrows<BpmnValidationException> {
            underTest.validate(models = listOf(model), engine = ProcessEngine.ZEEBE)
        }

        // then: the missing-element-id rule fires with ERROR severity
        assertThat(exception.violations).anyMatch {
            it.ruleId == "missing-element-id" && it.severity == Severity.ERROR
        }
    }

    @Test
    fun `collision detection detects collisions`() {
        // given: a model with two flow nodes that produce the same constant name
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "endEvent_complete"),
                FlowNodeDefinition.Unknown(id = "endEvent-complete"),
            ),
        )

        // when
        val exception = assertThrows<BpmnValidationException> {
            underTest.validate(models = listOf(model), engine = ProcessEngine.ZEEBE)
        }

        // then: the collision-detection rule fires
        assertThat(exception.violations).anyMatch { it.ruleId == "collision-detection" }
    }

    @Test
    fun `collision detection detects folding collisions`() {
        // given: two flow nodes whose ids keep distinct constants but fold to the same
        // PascalCase object name — previously emitted non-compiling generated code
        val model = testProcessModel(
            flowNodes = listOf(FlowNodeDefinition.Unknown(id = "foo"), FlowNodeDefinition.Unknown(id = "-foo")),
        )

        // when
        val exception = assertThrows<BpmnValidationException> {
            underTest.validate(models = listOf(model), engine = ProcessEngine.ZEEBE)
        }

        // then: the collision-detection rule fires
        assertThat(exception.violations).anyMatch { it.ruleId == "collision-detection" }
    }

    @Test
    fun `validation detects shared definition collisions across processes`() {
        // given: two processes whose job types normalize to the same constant
        val first = testProcessModel(processId = "first", flowNodes = listOf(jobWorkerTask(id = "task1", jobType = "newsletter.sendMail")))
        val second = testProcessModel(processId = "second", flowNodes = listOf(jobWorkerTask(id = "task2", jobType = "newsletter-sendMail")))

        // when
        val exception = assertThrows<BpmnValidationException> {
            underTest.validate(models = listOf(first, second), engine = ProcessEngine.ZEEBE)
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

        // when
        val exception = assertThrows<BpmnValidationException> {
            underTest.validate(models = listOf(first, second), engine = ProcessEngine.ZEEBE)
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

        // when
        val exception = assertThrows<BpmnValidationException> {
            underTest.validate(models = listOf(model), engine = ProcessEngine.ZEEBE)
        }

        // then: the collision-detection rule still fires despite being disabled
        assertThat(exception.violations).anyMatch { it.ruleId == "collision-detection" }
    }

    @Test
    fun `mandatory missing-element-id rule stays active even when disabled`() {
        // given: a service that tries to disable the mandatory missing-element-id rule
        val underTest = BpmnValidationService(ValidationConfig(disabledRules = setOf("missing-element-id")))
        val model = testProcessModel(flowNodes = listOf(FlowNodeDefinition.Unknown(id = null)))

        // when
        val exception = assertThrows<BpmnValidationException> {
            underTest.validate(models = listOf(model), engine = ProcessEngine.ZEEBE)
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
        val violations = underTest.collectSingleModelViolations(models = listOf(emptyModel, unimplementedModel), engine = ProcessEngine.ZEEBE)

        // then: only the given rule reports, the built-in service-task check does not run
        assertThat(violations.map { it.ruleId to it.processId }).containsExactly("empty-process" to "empty")
    }

    @Test
    fun `skips the cross-model rules while a model is unsound on its own`() {
        // given: a rule across models, and a model with a service task lacking an implementation
        val underTest = BpmnValidationService(rules = listOf(MissingServiceTaskImplementationRule(), RejectsEveryModelSetRule()))
        val model = testProcessModel(flowNodes = listOf(serviceTaskWithoutImplementation("task1")))

        // when
        val exception = assertThrows<BpmnValidationException> {
            underTest.validate(models = listOf(model), engine = ProcessEngine.ZEEBE)
        }

        // then: only the single-model rule reports
        assertThat(exception.violations).extracting("ruleId").containsExactly("missing-service-task-implementation")
    }

    @Test
    fun `runs the cross-model rules once every model is sound on its own`() {
        // given
        val underTest = BpmnValidationService(rules = listOf(MissingServiceTaskImplementationRule(), RejectsEveryModelSetRule()))

        // when
        val exception = assertThrows<BpmnValidationException> {
            underTest.validate(models = listOf(testProcessModel()), engine = ProcessEngine.ZEEBE)
        }

        // then
        assertThat(exception.violations).extracting("ruleId").containsExactly("rejects-every-model-set")
    }

    @Test
    fun `validateAndNormalize keeps files sharing a process id apart once their variant names differ`() {
        // given: two files of one process, one of them with a variant name
        val sources = listOf(
            SourcedProcessModel("corporate/order.bpmn", testProcessModel(variantName = "corporate")),
            SourcedProcessModel("default/order.bpmn", testProcessModel()),
        )

        // when
        val normalizedSources = underTest.validateAndNormalize(sources = sources, engine = ProcessEngine.ZEEBE) { it.apiName }

        // then: each file stays a model of its own, in the order of the names they are generated under
        assertThat(normalizedSources.map { it.fileName }).containsExactly("corporate/order.bpmn", "default/order.bpmn")
        assertThat(normalizedSources.map { it.model.apiName }).containsExactly("corporate_order", "order")
    }

    @Test
    fun `validateAndNormalize rejects a process id declared in several files without telling them apart`() {
        val sources = listOf(
            SourcedProcessModel("v1.bpmn", testProcessModel()),
            SourcedProcessModel("v2.bpmn", testProcessModel()),
        )

        assertThrows<ProcessApiNamingException> {
            underTest.validateAndNormalize(sources = sources, engine = ProcessEngine.ZEEBE) { it.apiName }
        }
    }

    @Test
    fun `validateAndNormalize validates each file before rejecting a shared process id`() {
        // given: two files of one process, one of them with a service task lacking an implementation
        val sources = listOf(
            SourcedProcessModel("v1.bpmn", testProcessModel(flowNodes = listOf(serviceTaskWithoutImplementation("task1")))),
            SourcedProcessModel("v2.bpmn", testProcessModel()),
        )

        // when
        val exception = assertThrows<BpmnValidationException> {
            underTest.validateAndNormalize(sources = sources, engine = ProcessEngine.ZEEBE) { it.apiName }
        }

        // then: the violation is reported, not the shared process id
        assertThat(exception.violations).anyMatch { it.ruleId == "missing-service-task-implementation" }
    }

    @Test
    fun `validateAndNormalize normalizes the models it returns`() {
        // given: a model whose flow nodes are unsorted
        val model = testProcessModel(flowNodes = listOf(FlowNodeDefinition.Unknown(id = "z-node"), FlowNodeDefinition.Unknown(id = "a-node")))
        val sources = listOf(SourcedProcessModel("order.bpmn", model))

        // when
        val normalizedSources = underTest.validateAndNormalize(sources = sources, engine = ProcessEngine.ZEEBE) { it.apiName }

        // then
        assertThat(normalizedSources.single().model.flowNodes.map { it.id }).containsExactly("a-node", "z-node")
    }

    private class RejectsEveryModelSetRule : CrossModelValidationRule {
        override val id = "rejects-every-model-set"
        override val severity = Severity.ERROR

        override fun validate(context: CrossModelValidationContext): List<ValidationViolation> = listOf(violation(processId = "any", message = "No set of models is allowed."))
    }
}
