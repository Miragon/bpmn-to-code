package io.miragon.bpmn.adapter.outbound.engine.utils

import io.miragon.bpmn.adapter.outbound.engine.helpers.MessageSource
import io.miragon.bpmn.domain.shared.FlowNodeProperties
import io.miragon.bpmn.domain.shared.MessageDirection
import org.camunda.bpm.model.bpmn.impl.BpmnModelConstants
import org.camunda.bpm.model.bpmn.instance.MessageEventDefinition
import org.camunda.bpm.model.bpmn.instance.ReceiveTask
import org.camunda.bpm.model.xml.ModelInstance

object MessageUtils {

    /**
     * Couples each message-bearing node to its message name and throw/catch role, keyed by element id.
     * Message events derive their role from the carrying event's shape (end / intermediate-throw =
     * [MessageDirection.THROW]; start / intermediate-catch / boundary = [MessageDirection.CATCH]); a
     * receive task is always a catcher. Nameless message nodes are skipped — they carry no name to
     * correlate on and are the concern of the missing-message-name rule.
     */
    fun ModelInstance.findMessageEventProperties(): Map<String, FlowNodeProperties.MessageEvent> {
        val fromEvents = this.getModelElementsByType(MessageEventDefinition::class.java)
            .mapNotNull { med ->
                val name = med.message?.getAttributeValue(BpmnModelConstants.BPMN_ATTRIBUTE_NAME) ?: return@mapNotNull null
                val parent = med.parentElement ?: return@mapNotNull null
                val elementId = parent.getAttributeValue(BpmnModelConstants.BPMN_ATTRIBUTE_ID) ?: return@mapNotNull null
                val direction = parent.elementType.typeName.toMessageDirection() ?: return@mapNotNull null
                elementId to FlowNodeProperties.MessageEvent(name, direction)
            }
        val fromTasks = this.getModelElementsByType(ReceiveTask::class.java)
            .mapNotNull { task ->
                val name = task.message?.getAttributeValue(BpmnModelConstants.BPMN_ATTRIBUTE_NAME) ?: return@mapNotNull null
                val elementId = task.getAttributeValue(BpmnModelConstants.BPMN_ATTRIBUTE_ID) ?: return@mapNotNull null
                elementId to FlowNodeProperties.MessageEvent(name, MessageDirection.CATCH)
            }
        return (fromEvents + fromTasks).toMap()
    }

    private fun String.toMessageDirection(): MessageDirection? = when (this) {
        "endEvent", "intermediateThrowEvent" -> MessageDirection.THROW
        "startEvent", "intermediateCatchEvent", "boundaryEvent" -> MessageDirection.CATCH
        else -> null
    }

    fun ModelInstance.findEventBasedMessagesWithSource(): List<MessageSource> {
        return this.getModelElementsByType(MessageEventDefinition::class.java)
            .mapNotNull { med ->
                val message = med.message ?: return@mapNotNull null
                val elementId = med.parentElement?.getAttributeValue(BpmnModelConstants.BPMN_ATTRIBUTE_ID)
                val name = message.getAttributeValue(BpmnModelConstants.BPMN_ATTRIBUTE_NAME)
                MessageSource(elementId, name, message)
            }
    }

    fun ModelInstance.findTaskBasedMessagesWithSource(): List<MessageSource> {
        return this.getModelElementsByType(ReceiveTask::class.java)
            .map { task ->
                val elementId = task.getAttributeValue(BpmnModelConstants.BPMN_ATTRIBUTE_ID)
                val name = task.message?.getAttributeValue(BpmnModelConstants.BPMN_ATTRIBUTE_NAME)
                MessageSource(elementId, name, task.message)
            }
    }

    fun ModelInstance.findAllMessagesWithSource(): List<MessageSource> =
        findEventBasedMessagesWithSource() + findTaskBasedMessagesWithSource()
}
