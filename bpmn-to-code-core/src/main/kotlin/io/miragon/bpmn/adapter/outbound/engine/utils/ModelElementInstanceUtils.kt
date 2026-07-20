package io.miragon.bpmn.adapter.outbound.engine.utils

import org.camunda.bpm.model.xml.instance.ModelElementInstance

object ModelElementInstanceUtils {

    fun List<ModelElementInstance>.findFirstByType(typeName: String): ModelElementInstance? {
        return firstOrNull { it.elementType.typeName == typeName }
    }

    fun List<ModelElementInstance>.filterByType(typeName: String): List<ModelElementInstance> {
        return filter { it.elementType.typeName == typeName }
    }

    fun List<ModelElementInstance>.extractAttribute(attributeName: String): List<String> {
        return mapNotNull { it.domElement.getAttribute(attributeName) }
    }

    fun ModelElementInstance.nonBlankAttribute(name: String): String? {
        return getAttributeValue(name)?.takeIf { it.isNotBlank() }
    }

    fun ModelElementInstance.nonBlankAttributeNs(namespace: String, name: String): String? {
        return getAttributeValueNs(namespace, name)?.takeIf { it.isNotBlank() }
    }

}
