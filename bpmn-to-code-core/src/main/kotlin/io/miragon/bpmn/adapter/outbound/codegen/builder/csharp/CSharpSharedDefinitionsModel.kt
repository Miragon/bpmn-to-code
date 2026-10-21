package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

/**
 * What the shared-definitions templates render. Strings are literals or identifiers.
 */
internal sealed interface CSharpSharedDefinitionsModel {

    val namespace: String
    val typeName: String
    val doc: String

    /**
     * Rendered by `shared-names.ftl`: `public const string MiraveloContractSigned = "miravelo.contractSigned";`
     */
    data class Names(
        override val namespace: String,
        override val typeName: String,
        override val doc: String,
        val constants: List<Constant>,
    ) : CSharpSharedDefinitionsModel

    /**
     * Rendered by `shared-names-and-codes.ftl`: a nested `public static class MiraveloApplicationInvalid` holding a
     * `Reference` and a `Code` constant.
     */
    data class NamesAndCodes(
        override val namespace: String,
        override val typeName: String,
        override val doc: String,
        val definitions: List<NameAndCode>,
    ) : CSharpSharedDefinitionsModel

    data class Constant(val name: String, val value: String)

    /**
     * e.g. [className] `MiraveloApplicationInvalid`, [referenceName] `Reference`, [reference]
     * `"miravelo.applicationInvalid"`, [codeName] `Code`, [code] `"applicationInvalid"`
     */
    data class NameAndCode(
        val className: String,
        val referenceName: String,
        val reference: String,
        val codeName: String,
        val code: String,
    )
}
