package io.miragon.bpmn.adapter.outbound.engine.xml

import org.camunda.bpm.model.bpmn.instance.BaseElement
import org.camunda.bpm.model.xml.instance.DomElement
import org.camunda.bpm.model.xml.instance.ModelElementInstance

/**
 * The handful of lookups the `camunda-xml-model` API does not offer directly:
 * Reaching into bpmn:extensionElements`, filtering by element type or name, and reading attributes that may be blank.
 *
 * Everything here is a thin projection over the parser's own types. Anything that interprets what it finds
 * belongs to a dialect instead.
 */
internal object CamundaXmlApi {

    fun BaseElement.findExtensionElements(): List<ModelElementInstance> = this.extensionElements?.elementsQuery?.list() ?: emptyList()

    fun BaseElement.findExtensionElementsWithType(type: String): List<ModelElementInstance> = this.findExtensionElements().filterByType(type)

    fun BaseElement.findExtensionElement(type: String): ModelElementInstance? = this.findExtensionElementsWithType(type).firstOrNull()

    fun List<ModelElementInstance>.findFirstByType(typeName: String): ModelElementInstance? = firstOrNull { it.elementType.typeName == typeName }

    fun List<ModelElementInstance>.filterByType(typeName: String): List<ModelElementInstance> = filter { it.elementType.typeName == typeName }

    fun List<ModelElementInstance>.extractAttribute(attributeName: String): List<String> = mapNotNull { it.domElement.getAttribute(attributeName) }

    fun ModelElementInstance.nonBlankAttribute(name: String): String? = getAttributeValue(name)?.takeIf { it.isNotBlank() }

    fun ModelElementInstance.nonBlankAttributeNs(namespace: String, name: String): String? = getAttributeValueNs(namespace, name)?.takeIf { it.isNotBlank() }

    fun List<DomElement>.withElementName(vararg names: String): List<DomElement> = filter { names.contains(it.localName) }

    fun List<DomElement>.withAttribute(pair: Pair<String, String>): List<DomElement> {
        val (attributeName, expectedValue) = pair
        return filter { it.getAttribute(attributeName) == expectedValue }
    }
}
