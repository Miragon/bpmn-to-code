package io.miragon.bpmn.domain.shared

/**
 * Programming languages supported by the process-api generator as output
 */
enum class OutputLanguage {
    KOTLIN,
    JAVA,

    /**
     * Beta. Emits the constants sections only — the typed navigation DSL is JVM-only until a
     * C# counterpart of `bpmn-to-code-runtime` exists.
     */
    CSHARP,
}
