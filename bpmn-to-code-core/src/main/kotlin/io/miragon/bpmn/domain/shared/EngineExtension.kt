package io.miragon.bpmn.domain.shared

/**
 * A verbatim projection of a foreign-namespace XML element below `bpmn:extensionElements`.
 *
 * This is the lossless escape hatch for engine data bpmn-to-code does not normalise: [type] carries the
 * namespace prefix (`zeebe:taskHeaders`, `camunda:properties`), [attributes] the element's own attributes,
 * [children] its nested elements, and [body] its text content. Structure and namespace provenance are
 * preserved, so a new engine feature needs no model change. See ADR 017.
 */
data class EngineExtension(
    val type: String,
    val attributes: Map<String, String> = emptyMap(),
    val children: List<EngineExtension> = emptyList(),
    val body: String? = null,
)
