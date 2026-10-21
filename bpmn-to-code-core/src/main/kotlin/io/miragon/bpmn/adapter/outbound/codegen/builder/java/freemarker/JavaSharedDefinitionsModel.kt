package io.miragon.bpmn.adapter.outbound.codegen.builder.java.freemarker

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.TypeReferences

/**
 * What `shared-definitions.ftl` renders. Strings are literals or identifiers; the template writes each constant in the
 * form its [shape] gives. Types are read through [types].
 *
 * @property elementType the runtime type of every constant, e.g. `MessageName`
 */
internal data class JavaSharedDefinitionsModel(
    val typeName: String,
    val doc: List<String>,
    val shape: SharedDefinitionType.Shape,
    val elementType: String,
    val constants: List<Constant>,
    val types: TypeReferences,
) {

    /**
     * e.g. [name] `MIRAVELO_APPLICATION_INVALID`, [value] `"miravelo.applicationInvalid"`,
     * [code] `"applicationInvalid"`
     *
     * @property code set for errors and escalations only
     */
    data class Constant(val name: String, val value: String, val code: String?)
}
