package io.miragon.bpmn.adapter.outbound.engine.bpmn

import io.miragon.bpmn.adapter.outbound.engine.xml.CamundaXmlApi.findExtensionElements
import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.shared.RootElements
import org.camunda.bpm.model.bpmn.impl.BpmnModelConstants
import org.camunda.bpm.model.bpmn.instance.Error
import org.camunda.bpm.model.bpmn.instance.Escalation
import org.camunda.bpm.model.bpmn.instance.Message
import org.camunda.bpm.model.bpmn.instance.Process
import org.camunda.bpm.model.bpmn.instance.Signal
import org.camunda.bpm.model.xml.ModelInstance
import org.camunda.bpm.model.xml.instance.ModelElementInstance

/**
 * Process-level metadata and the `bpmn:Definitions` root-element registries — the parts of a BPMN file
 * that are identical across engines. Per-node structure is read by `BpmnStructureReader`.
 */
internal object BpmnDefinitionsReader {

    fun ModelInstance.getProcessId(): String {
        val process = this.findProcess()
        val processId = process.getAttributeValue(BpmnModelConstants.BPMN_ATTRIBUTE_ID)
        requireNotNull(processId) { "Process element is missing an 'id' attribute" }
        return processId
    }

    fun ModelInstance.getProcessName(): String? {
        val raw = this.findProcess().getAttributeValue(BpmnModelConstants.BPMN_ATTRIBUTE_NAME)
        return raw?.normalizeWhitespace()?.takeIf { it.isNotBlank() }
    }

    fun ModelInstance.isExecutable(): Boolean {
        val process = this.findProcess()
        val raw = process.getAttributeValue(BpmnModelConstants.BPMN_ATTRIBUTE_IS_EXECUTABLE)
        return raw?.toBoolean() ?: true
    }

    fun ModelInstance.extractVariantName(): String? {
        val process = this.findProcess()
        val extensions = process.findExtensionElements()
        val propertiesContainers = extensions.filter { it.domElement.localName == "properties" }
        val allProperties = propertiesContainers.flatMap { it.domElement.childElements }
        val variantProperty = allProperties
            .filter { it.localName == "property" }
            .firstOrNull { it.getAttribute("name") == BpmnExtensionConstants.VARIANT_NAME_PROPERTY_NAME }
        return variantProperty?.getAttribute("value")?.takeIf { it.isNotBlank() }
    }

    fun ModelInstance.findProcess(): Process {
        val process = this.getModelElementsByType(Process::class.java).firstOrNull()
        requireNotNull(process) { "BPMN model does not contain a Process element" }
        return process
    }

    /**
     * The `bpmn:Definitions` root-element registries, each keyed by the element's own id — several events
     * may reference the same message, signal, error or escalation.
     *
     * [correlationKeyOf] resolves the engine's correlation-key expression, which BPMN declares on the
     * message element rather than on the events referencing it. It is the only engine-specific part of a
     * root element, which is why it arrives as a function instead of pulling the whole dialect in here.
     */
    fun ModelInstance.readRootElements(correlationKeyOf: (Message) -> String?): RootElements {
        return RootElements(
            messages = registryOf(Message::class.java) {
                RootElementDefinition.Message(id = it.id ?: it.name, name = it.name, correlationKey = correlationKeyOf(it))
            },
            signals = registryOf(Signal::class.java) {
                RootElementDefinition.Signal(id = it.id ?: it.name, name = it.name)
            },
            errors = registryOf(Error::class.java) {
                RootElementDefinition.Error(id = it.id ?: it.name, name = it.name, code = it.errorCode)
            },
            escalations = registryOf(Escalation::class.java) {
                RootElementDefinition.Escalation(id = it.id ?: it.name, name = it.name, code = it.escalationCode)
            },
        )
    }

    private fun <E : ModelElementInstance, D : RootElementDefinition> ModelInstance.registryOf(
        type: Class<E>,
        toDefinition: (E) -> D,
    ): List<D> {
        return getModelElementsByType(type).map(toDefinition).distinctBy { it.id }
    }

    fun String.normalizeWhitespace(): String = this.replace(Regex("\\s+"), " ").trim()
}
