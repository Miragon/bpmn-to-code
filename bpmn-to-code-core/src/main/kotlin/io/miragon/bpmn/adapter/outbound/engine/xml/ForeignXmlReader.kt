package io.miragon.bpmn.adapter.outbound.engine.xml

import io.miragon.bpmn.domain.shared.EngineExtension
import org.camunda.bpm.model.xml.ModelInstance
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Projects foreign-namespace XML — everything a BPMN file carries beyond the OMG schema — into the
 * engine-agnostic [EngineExtension] shape, plus the foreign-namespace *attributes* of an element.
 *
 * Camunda's typed model API only exposes attributes it declares, so anything an engine adds that we have
 * not modelled would be invisible. Reading the underlying W3C document instead keeps the projection
 * lossless and future-proof: a new `zeebe:` or `camunda:` element shows up without a code change.
 */
internal class ForeignXmlReader(
    modelInstance: ModelInstance,
    private val engineNamespace: String = "",
    private val fullyReadExtensions: Set<String> = emptySet(),
) {

    private val elementsById: Map<String, Element> = indexById(modelInstance.document.domSource.node)

    /**
     * The `bpmn:extensionElements` children of the element with [elementId], projected recursively.
     */
    fun extensionsOf(elementId: String?): List<EngineExtension> {
        val element = elementsById[elementId] ?: return emptyList()
        return element.childElements()
            .filter { it.localNameOf() == EXTENSION_ELEMENTS && it.namespaceURI in BPMN_NAMESPACES }
            .flatMap { it.childElements() }
            .filterNot { it.isFullyReadByTheDialect() }
            .map { it.toExtension() }
    }

    /**
     * Attributes of the element with [elementId] that live in a non-BPMN namespace — Camunda 7's
     * `camunda:asyncBefore`, Operaton's `operaton:exclusive`, and anything else an engine adds.
     * Keys keep their `prefix:localName` form so provenance is never lost. Values are typed where the
     * literal is unambiguous (`true` / `false` / an integer), and kept as strings otherwise.
     */
    fun foreignAttributesOf(elementId: String?, fullyRead: Set<String> = emptySet()): Map<String, Any?> {
        val element = elementsById[elementId] ?: return emptyMap()
        val attributes = element.attributes ?: return emptyMap()
        return (0 until attributes.length)
            .map { attributes.item(it) }
            .filter { it.namespaceURI != null && it.namespaceURI !in IGNORED_NAMESPACES }
            .filterNot { it.namespaceURI == engineNamespace && it.localNameOf() in fullyRead }
            .associate { it.qualifiedName() to it.nodeValue.toTypedValue() }
    }

    /**
     * Whether the dialect already read this element into a typed field, in which case reporting it here
     * would duplicate it. Matched on namespace *and* local name, so an identically named element from
     * another engine is unaffected.
     */
    private fun Element.isFullyReadByTheDialect(): Boolean = namespaceURI == engineNamespace && localNameOf() in fullyReadExtensions

    private fun Element.toExtension(): EngineExtension {
        val children = childElements()
        return EngineExtension(
            type = qualifiedName(),
            attributes = ownAttributes(),
            children = children.map { it.toExtension() },
            body = textContent.takeIf { children.isEmpty() && it.isNotBlank() }?.trim(),
        )
    }

    private fun Element.ownAttributes(): Map<String, String> {
        val attributes = attributes ?: return emptyMap()
        return (0 until attributes.length)
            .map { attributes.item(it) }
            .filter { it.namespaceURI !in IGNORED_NAMESPACES }
            .associate { it.qualifiedName() to it.nodeValue }
    }

    private fun indexById(root: Node): Map<String, Element> {
        val document = root as? Document ?: return emptyMap()
        return buildMap { collectById(document.documentElement, this) }
    }

    private fun collectById(element: Element, target: MutableMap<String, Element>) {
        element.getAttribute(ID_ATTRIBUTE).takeIf { it.isNotBlank() }?.let { target.putIfAbsent(it, element) }
        element.childElements().forEach { collectById(it, target) }
    }

    private fun Element.childElements(): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    private fun Node.qualifiedName(): String {
        val local = localNameOf()
        return prefix?.let { "$it:$local" } ?: local
    }

    private fun Node.localNameOf(): String = localName ?: nodeName

    private fun String?.toTypedValue(): Any? = when {
        this == null -> null
        equals("true", ignoreCase = true) -> true
        equals("false", ignoreCase = true) -> false
        else -> toLongOrNull() ?: this
    }

    private companion object {
        const val ID_ATTRIBUTE = "id"
        const val EXTENSION_ELEMENTS = "extensionElements"

        val BPMN_NAMESPACES = setOf(
            "http://www.omg.org/spec/BPMN/20100524/MODEL",
        )

        val IGNORED_NAMESPACES = setOf(
            "http://www.w3.org/2000/xmlns/",
            "http://www.w3.org/2001/XMLSchema-instance",
        )
    }
}
