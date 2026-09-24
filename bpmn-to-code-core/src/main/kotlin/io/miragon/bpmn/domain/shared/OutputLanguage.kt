package io.miragon.bpmn.domain.shared

/**
 * Programming languages supported by the process-api generator as output
 */
enum class OutputLanguage {
    KOTLIN,
    JAVA,

    /**
     * Same API surface as Kotlin and Java; the runtime types the `Flow` nodes need are inlined into each
     * generated file, so the output has no package dependency.
     */
    CSHARP,
}
