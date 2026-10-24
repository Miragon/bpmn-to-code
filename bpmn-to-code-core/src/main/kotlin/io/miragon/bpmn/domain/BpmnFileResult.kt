package io.miragon.bpmn.domain

/**
 * A Process API generated from [sourceFiles], as every entry point reports it.
 */
data class BpmnFileResult(val processId: String, val sourceFiles: List<String>) {

    fun describe(): String = "Generated: $processId (from ${sourceFiles.joinToString(", ")})"

    companion object {

        fun summaryOf(results: List<BpmnFileResult>): String {
            if (results.isEmpty()) return "No BPMN models found"
            return "BPMN models generated successfully (${results.size} models)"
        }
    }
}
