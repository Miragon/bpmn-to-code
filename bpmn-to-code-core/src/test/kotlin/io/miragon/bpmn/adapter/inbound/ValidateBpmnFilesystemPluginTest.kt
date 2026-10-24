package io.miragon.bpmn.adapter.inbound

import io.miragon.bpmn.application.port.inbound.ValidateBpmnFromFilesystemUseCase
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.validation.model.ValidationConfig
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class ValidateBpmnFilesystemPluginTest {

    private val useCase = mockk<ValidateBpmnFromFilesystemUseCase>(relaxed = true)
    private val underTest = ValidateBpmnFilesystemPlugin(useCase = useCase)

    @Test
    fun `execute delegates to use case with correct command`() {
        // given: a config
        val config = ValidationConfig(failOnWarning = true, disabledRules = setOf("missing-element-id"))

        // when: execute is called with all parameters
        underTest.execute(
            baseDir = "/path/to/bpmn",
            filePattern = "*.bpmn",
            engine = ProcessEngine.ZEEBE,
            validationConfig = config,
        )

        // then: use case is called with the correct command
        verify {
            useCase.validateBpmn(
                ValidateBpmnFromFilesystemUseCase.Command(
                    baseDir = "/path/to/bpmn",
                    filePattern = "*.bpmn",
                    engine = ProcessEngine.ZEEBE,
                    validationConfig = config,
                ),
            )
        }
        confirmVerified(useCase)
    }

    @Test
    fun `execute uses default validation config when not provided`() {
        // when: execute is called without explicit validationConfig
        underTest.execute(
            baseDir = "/path/to/bpmn",
            filePattern = "*.bpmn",
            engine = ProcessEngine.CAMUNDA_7,
        )

        // then: use case is called with a default validation config
        verify {
            useCase.validateBpmn(
                ValidateBpmnFromFilesystemUseCase.Command(
                    baseDir = "/path/to/bpmn",
                    filePattern = "*.bpmn",
                    engine = ProcessEngine.CAMUNDA_7,
                    validationConfig = ValidationConfig(),
                ),
            )
        }
        confirmVerified(useCase)
    }
}
