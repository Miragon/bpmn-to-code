package io.miragon.bpmn.adapter.outbound.codegen

/**
 * What [CodeGenerationAdapter] needs from one output language: the extension of its files and the builders that
 * render the language-neutral models to source text.
 */
internal class LanguageGenerator(
    val fileExtension: String,
    val processApiBuilder: ProcessApiBuilder,
    val sharedDefinitionsBuilder: SharedDefinitionsBuilder,
)
