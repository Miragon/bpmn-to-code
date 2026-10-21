package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

/**
 * What `shared-definitions.ftl` renders.
 * Strings are literals, identifiers or references, and the template writes the code around them.
 *
 * @property elementType the type `all` lists, e.g. `string` or `Runtime.BpmnErrorDefinition`
 */
internal data class TypeScriptSharedDefinitionsModel(
    val typeName: String,
    val doc: String,
    val elementType: String,
    val constants: List<Constant>,
) {

    /**
     * Rendered as `MIRAVELO_CONTRACT_SIGNED: "miravelo.contractSigned"`, or, with a [code], as
     * `MIRAVELO_APPLICATION_INVALID: { name: "miravelo.applicationInvalid", code: "applicationInvalid" }`.
     */
    data class Constant(val key: String, val value: String, val code: String?)
}
