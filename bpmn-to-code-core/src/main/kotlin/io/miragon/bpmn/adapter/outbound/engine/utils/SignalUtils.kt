package io.miragon.bpmn.adapter.outbound.engine.utils

import io.miragon.bpmn.domain.shared.EventDirection
import io.miragon.bpmn.domain.shared.FlowNodeProperties
import org.camunda.bpm.model.bpmn.impl.BpmnModelConstants
import org.camunda.bpm.model.bpmn.instance.SignalEventDefinition
import org.camunda.bpm.model.xml.ModelInstance

object SignalUtils {

    /**
     * Couples each signal-bearing node to its signal name and throw/catch role, keyed by element id.
     * Signal events derive their role from the carrying event's shape (end / intermediate-throw =
     * [EventDirection.THROW]; start / intermediate-catch / boundary = [EventDirection.CATCH]). Nameless
     * signal nodes are skipped — they carry no name to correlate on and are the concern of the
     * missing-signal-name rule.
     */
    fun ModelInstance.findSignalEventProperties(): Map<String, FlowNodeProperties.SignalEvent> {
        return this.getModelElementsByType(SignalEventDefinition::class.java)
            .mapNotNull { sed ->
                val name = sed.signal?.getAttributeValue(BpmnModelConstants.BPMN_ATTRIBUTE_NAME) ?: return@mapNotNull null
                val parent = sed.parentElement ?: return@mapNotNull null
                val elementId = parent.getAttributeValue(BpmnModelConstants.BPMN_ATTRIBUTE_ID) ?: return@mapNotNull null
                val direction = EventDirectionUtils.fromElementTypeName(parent.elementType.typeName) ?: return@mapNotNull null
                elementId to FlowNodeProperties.SignalEvent(name, direction)
            }
            .toMap()
    }
}
