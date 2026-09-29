package io.miragon.bpmn.adapter.outbound.engine.bpmn

import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.MessageReference
import io.miragon.bpmn.domain.shared.TimerType
import org.camunda.bpm.model.bpmn.instance.CompensateEventDefinition
import org.camunda.bpm.model.bpmn.instance.ConditionalEventDefinition
import org.camunda.bpm.model.bpmn.instance.ErrorEventDefinition
import org.camunda.bpm.model.bpmn.instance.EscalationEventDefinition
import org.camunda.bpm.model.bpmn.instance.EventDefinition
import org.camunda.bpm.model.bpmn.instance.FlowNode
import org.camunda.bpm.model.bpmn.instance.LinkEventDefinition
import org.camunda.bpm.model.bpmn.instance.Message
import org.camunda.bpm.model.bpmn.instance.MessageEventDefinition
import org.camunda.bpm.model.bpmn.instance.SignalEventDefinition
import org.camunda.bpm.model.bpmn.instance.TerminateEventDefinition
import org.camunda.bpm.model.bpmn.instance.TimerEventDefinition

/**
 * Reads the event definitions of an event into the domain's [EventDefinitionInstance]s. They are standard BPMN,
 * so every engine reads them the same way.
 */
internal object EventDefinitionReader {

    fun eventDefinitionsOf(node: FlowNode): List<EventDefinitionInstance> {
        val definitions = node.getChildElementsByType(EventDefinition::class.java)
        return definitions.mapNotNull { it.toInstance() }
    }

    /**
     * Message events and send or receive tasks refer to a message the same way.
     */
    fun referenceOf(message: Message): MessageReference = MessageReference(
        messageRef = message.id ?: message.name,
        messageName = message.name,
    )

    @Suppress("CyclomaticComplexMethod")
    private fun EventDefinition.toInstance(): EventDefinitionInstance? = when (this) {
        is TimerEventDefinition -> toTimer()

        is MessageEventDefinition -> EventDefinitionInstance.Message(
            reference = message?.let { referenceOf(it) } ?: MessageReference(),
        )

        is SignalEventDefinition -> EventDefinitionInstance.Signal(
            signalRef = signal?.let { it.id ?: it.name },
            signalName = signal?.name,
        )

        is ErrorEventDefinition -> EventDefinitionInstance.Error(
            errorRef = error?.let { it.id ?: it.name },
            errorName = error?.name,
            errorCode = error?.errorCode,
        )

        is EscalationEventDefinition -> EventDefinitionInstance.Escalation(
            escalationRef = escalation?.let { it.id ?: it.name },
            escalationName = escalation?.name,
            escalationCode = escalation?.escalationCode,
        )

        is CompensateEventDefinition -> EventDefinitionInstance.Compensation(
            activityRef = activity?.id,
            waitForCompletion = isWaitForCompletion,
        )

        is ConditionalEventDefinition -> EventDefinitionInstance.Conditional(
            expression = condition?.textContent?.takeIf { it.isNotBlank() },
        )

        is LinkEventDefinition -> EventDefinitionInstance.Link(linkName = name)

        is TerminateEventDefinition -> EventDefinitionInstance.Terminate

        else -> null
    }

    private fun TimerEventDefinition.toTimer(): EventDefinitionInstance.Timer = when {
        timeDate != null -> EventDefinitionInstance.Timer(TimerType.DATE, timeDate.textContent)
        timeDuration != null -> EventDefinitionInstance.Timer(TimerType.DURATION, timeDuration.textContent)
        timeCycle != null -> EventDefinitionInstance.Timer(TimerType.CYCLE, timeCycle.textContent)
        else -> EventDefinitionInstance.Timer()
    }
}
