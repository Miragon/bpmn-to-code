package io.miragon.bpmn.domain.shared

/**
 * Programming languages supported by the process-api generator as output
 */
enum class OutputLanguage {
    KOTLIN,
    JAVA,

    /**
     * Same API surface as Kotlin and Java, on the NuGet runtime package `Miragon.BpmnToCode.Runtime`.
     */
    CSHARP,

    /**
     * Experimental. Same API surface as Kotlin, one singleton class per node, on the npm runtime package
     * `@miragon/bpmn-to-code-runtime`.
     */
    TYPESCRIPT,
}
