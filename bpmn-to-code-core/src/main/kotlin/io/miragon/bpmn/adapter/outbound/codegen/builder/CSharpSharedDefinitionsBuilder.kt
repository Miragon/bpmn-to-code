package io.miragon.bpmn.adapter.outbound.codegen.builder

import io.miragon.bpmn.adapter.outbound.codegen.CodeGenerationAdapter
import io.miragon.bpmn.adapter.outbound.codegen.writer.CSharpWriter
import io.miragon.bpmn.adapter.outbound.codegen.writer.CSharpWriter.Companion.toPascalCase
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.SharedDefinitionsApi
import io.miragon.bpmn.domain.shared.VariableMapping

/**
 * Generates one C# static class per kind of engine-global identifier, shared by all Process APIs of a
 * run — **beta**, constants only like [CSharpProcessApiBuilder].
 */
internal class CSharpSharedDefinitionsBuilder : CodeGenerationAdapter.AbstractSharedDefinitionsBuilder() {

    override fun buildApiFiles(api: SharedDefinitionsApi): List<GeneratedApiFile> {
        val definitions = api.definitions
        return listOfNotNull(
            constants(api, "ServiceTasks", "Job worker task types — the task type a C# worker subscribes to.", definitions.serviceTasks),
            constants(api, "Messages", "BPMN message names used to correlate messages to running process instances.", definitions.messages),
            constants(api, "Signals", "BPMN signal names broadcast and caught by signal events.", definitions.signals),
            nameAndCodes(api, "Errors", "BPMN error definitions with name and code, as thrown and caught by the processes.", definitions.errors),
            nameAndCodes(api, "Escalations", "BPMN escalation definitions with name and code, as thrown and caught by the processes.", definitions.escalations),
        )
    }

    private fun constants(api: SharedDefinitionsApi, className: String, doc: String, items: List<VariableMapping<String>>): GeneratedApiFile? = items.ifNotEmpty {
        toFile(api, className, doc) { writer ->
            items.forEach { writer.constant(it.getRawName().toPascalCase(), it.getValue()) }
        }
    }

    private fun nameAndCodes(api: SharedDefinitionsApi, className: String, doc: String, items: List<VariableMapping<Pair<String, String>>>): GeneratedApiFile? = items.ifNotEmpty {
        toFile(api, className, doc) { writer ->
            writer.forEachSeparated(items) { item ->
                val (name, code) = item.getValue()
                writer.staticClass(item.getRawName().toPascalCase()) {
                    writer.constant("Reference", name)
                    writer.constant("Code", code)
                }
            }
        }
    }

    private fun toFile(api: SharedDefinitionsApi, className: String, doc: String, body: (CSharpWriter) -> Unit): GeneratedApiFile {
        val writer = CSharpWriter()
        writer.line("// $autoGenComment")
        writer.line("namespace ${api.packagePath};")
        writer.line()
        writer.docComment(doc)
        writer.staticClass(className) { body(writer) }
        return GeneratedApiFile(
            fileName = "$className.cs",
            packagePath = api.packagePath,
            content = writer.render(),
            language = api.outputLanguage,
            processId = null,
        )
    }

    private fun <T> List<T>.ifNotEmpty(build: () -> GeneratedApiFile): GeneratedApiFile? = if (isEmpty()) null else build()
}
