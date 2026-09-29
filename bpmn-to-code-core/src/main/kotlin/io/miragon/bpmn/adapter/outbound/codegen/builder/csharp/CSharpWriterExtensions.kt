package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

/**
 * A static read-only list with one element per line. Expression-bodied, so it never takes part in static
 * initialisation.
 */
internal fun CSharpWriter.staticListProperty(name: String, elementType: String, elements: List<String>, doc: String? = null) {
    doc?.let { docComment(it) }
    line("public static System.Collections.Generic.IReadOnlyList<$elementType> $name => new $elementType[]")
    line("{")
    elements.forEach { line("    $it,") }
    line("};")
}
