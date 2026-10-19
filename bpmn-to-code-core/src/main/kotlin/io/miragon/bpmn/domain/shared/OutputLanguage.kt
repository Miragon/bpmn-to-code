package io.miragon.bpmn.domain.shared

/**
 * Programming languages supported by the process-api generator as output
 */
enum class OutputLanguage {
    KOTLIN,
    JAVA,

    /**
     * Same API surface as Kotlin and Java; the runtime types and `ProcessPath` come from the NuGet package
     * `Miragon.BpmnToCode.Runtime`.
     */
    CSHARP,

    /**
     * Same API surface as the other languages; the runtime types and `ProcessPath` come from the npm package
     * `@miragon/bpmn-to-code-runtime`.
     */
    TYPESCRIPT,
}
