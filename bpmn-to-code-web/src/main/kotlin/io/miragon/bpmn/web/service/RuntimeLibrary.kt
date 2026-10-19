package io.miragon.bpmn.web.service

import io.miragon.bpmn.domain.shared.OutputLanguage

/**
 * The runtime library each output language's generated code imports from, with the resource directory its bundled
 * sources are copied to by the build.
 */
enum class RuntimeLibrary(
    val resourceDirectory: String,
    val packageName: String,
    val importedFrom: String,
    val sourcesDirectory: String,
) {
    JVM(
        resourceDirectory = "kotlin",
        packageName = "io.miragon:bpmn-to-code-runtime",
        importedFrom = "io.miragon.bpmn.runtime",
        sourcesDirectory = "io/miragon/bpmn/runtime",
    ),
    CSHARP(
        resourceDirectory = "csharp",
        packageName = "Miragon.BpmnToCode.Runtime",
        importedFrom = "Miragon.BpmnToCode.Runtime",
        sourcesDirectory = "Miragon.BpmnToCode.Runtime",
    ),
    TYPESCRIPT(
        resourceDirectory = "typescript",
        packageName = "@miragon/bpmn-to-code-runtime",
        importedFrom = "@miragon/bpmn-to-code-runtime",
        sourcesDirectory = "bpmn-to-code-runtime",
    ),
    ;

    companion object {
        fun of(language: OutputLanguage): RuntimeLibrary = when (language) {
            OutputLanguage.KOTLIN, OutputLanguage.JAVA -> JVM
            OutputLanguage.CSHARP -> CSHARP
            OutputLanguage.TYPESCRIPT -> TYPESCRIPT
        }
    }
}
