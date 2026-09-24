package io.miragon.bpmn.adapter.outbound.codegen.builder

import io.miragon.bpmn.adapter.outbound.codegen.ApiObjectSelection
import io.miragon.bpmn.adapter.outbound.codegen.ApiObjectType
import io.miragon.bpmn.adapter.outbound.codegen.CodeGenerationAdapter
import io.miragon.bpmn.adapter.outbound.codegen.writer.CSharpWriter
import io.miragon.bpmn.adapter.outbound.codegen.writer.CSharpWriter.Companion.toPascalCase
import io.miragon.bpmn.adapter.outbound.codegen.writer.ObjectWriter
import io.miragon.bpmn.domain.BpmnModelApi
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.shared.CallActivityDefinition
import io.miragon.bpmn.domain.utils.StringUtils.toUpperSnakeCase

/**
 * Generates the process API for a single BPMN process as a C# file — **beta**.
 *
 * Only the constants sections are emitted. `Flow` and `Variants` are navigation over the process
 * graph, and every node of it extends types from `bpmn-to-code-runtime`, which exists for the JVM only;
 * until a C# counterpart ships, a partial navigation API would not compile. Everything else the Kotlin and
 * Java builders express through wrapper types (`ElementId`, `MessageName`, `BpmnError`, …) is a plain
 * `const string` here, so the generated file has no dependencies at all.
 */
internal class CSharpProcessApiBuilder : CodeGenerationAdapter.AbstractProcessApiBuilder<CSharpWriter>() {

    private val objectWriters: Map<ApiObjectType, ObjectWriter<CSharpWriter>> = mapOf(
        ApiObjectType.PROCESS_ID to ProcessIdWriter(),
        ApiObjectType.PROCESS_ENGINE to ProcessEngineWriter(),
        ApiObjectType.ELEMENTS to ElementsWriter(),
        ApiObjectType.CALL_ACTIVITIES to CallActivitiesWriter(),
        ApiObjectType.MESSAGES to MessagesWriter(),
        ApiObjectType.SERVICE_TASKS to ServiceTasksWriter(),
        ApiObjectType.TIMERS to TimersWriter(),
        ApiObjectType.ERRORS to ErrorsWriter(),
        ApiObjectType.ESCALATIONS to EscalationsWriter(),
        ApiObjectType.SIGNALS to SignalsWriter(),
        ApiObjectType.VARIABLES to VariablesWriter(),
    )

    override fun buildApiFile(modelApi: BpmnModelApi): GeneratedApiFile {
        val writer = CSharpWriter()
        writer.line("// $autoGenComment")
        writer.line("namespace ${modelApi.packagePath};")
        writer.line()

        val sections = objectWriters.filterKeys { ApiObjectSelection.includes(it, modelApi) }.values.toList()
        writer.staticClass(modelApi.fileName()) {
            writer.forEachSeparated(sections) { section -> section.addTo(writer, modelApi) }
        }

        return GeneratedApiFile(
            fileName = "${modelApi.fileName()}.cs",
            packagePath = modelApi.packagePath,
            content = writer.render(),
            language = modelApi.outputLanguage,
            processId = modelApi.model.processId,
        )
    }

    private class ProcessIdWriter : ObjectWriter<CSharpWriter> {

        override fun addTo(builder: CSharpWriter, modelApi: BpmnModelApi) {
            builder.constant("ProcessId", modelApi.model.processId)
        }
    }

    private class ProcessEngineWriter : ObjectWriter<CSharpWriter> {

        override fun addTo(builder: CSharpWriter, modelApi: BpmnModelApi) {
            builder.constant("ProcessEngine", modelApi.targetEngine.name)
        }
    }

    private class ElementsWriter : ObjectWriter<CSharpWriter> {

        override fun addTo(builder: CSharpWriter, modelApi: BpmnModelApi) {
            builder.docComment(
                """
                BPMN element ids as declared in the source model.
                Typically used in process-level tests or when searching for tasks.
                Worker runtime code rarely needs these.
                """.trimIndent(),
            )
            builder.staticClass("Elements") {
                modelApi.model.allFlowNodes.sortedBy { it.getRawName() }.forEach { flowNode ->
                    builder.constant(flowNode.getRawName().toPascalCase(), flowNode.getValue())
                }
            }
        }
    }

    private class MessagesWriter : ObjectWriter<CSharpWriter> {

        override fun addTo(builder: CSharpWriter, modelApi: BpmnModelApi) {
            builder.docComment("BPMN message names used to correlate messages to running process instances.")
            builder.staticClass("Messages") {
                modelApi.model.definitions.messages.asApiConstants().forEach { message ->
                    builder.constant(message.getRawName().toPascalCase(), message.getValue())
                }
            }
        }
    }

    private class ServiceTasksWriter : ObjectWriter<CSharpWriter> {

        override fun addTo(builder: CSharpWriter, modelApi: BpmnModelApi) {
            builder.docComment("Job worker task types — the task type a C# worker subscribes to.")
            builder.staticClass("ServiceTasks") {
                modelApi.model.serviceTasks.asApiConstants().forEach { task ->
                    builder.constant(task.getRawName().toPascalCase(), task.getValue())
                }
            }
        }
    }

    private class SignalsWriter : ObjectWriter<CSharpWriter> {

        override fun addTo(builder: CSharpWriter, modelApi: BpmnModelApi) {
            builder.docComment("BPMN signal names broadcast and caught by signal events.")
            builder.staticClass("Signals") {
                modelApi.model.definitions.signals.asApiConstants().forEach { signal ->
                    builder.constant(signal.getRawName().toPascalCase(), signal.getValue())
                }
            }
        }
    }

    private class TimersWriter : ObjectWriter<CSharpWriter> {

        override fun addTo(builder: CSharpWriter, modelApi: BpmnModelApi) {
            builder.docComment("Timer definitions of timer events, with their type (Date, Duration or Cycle) and expression.")
            builder.staticClass("Timers") {
                builder.forEachSeparated(modelApi.model.timers) { timer ->
                    val (timerType, timerValue) = timer.getValue()
                    builder.staticClass(timer.getRawName().toPascalCase()) {
                        builder.constant("Type", timerType)
                        builder.constant("Value", timerValue)
                    }
                }
            }
        }
    }

    private class ErrorsWriter : ObjectWriter<CSharpWriter> {

        override fun addTo(builder: CSharpWriter, modelApi: BpmnModelApi) {
            builder.docComment("BPMN error definitions with name and code, as thrown and caught by the process.")
            builder.staticClass("Errors") {
                builder.forEachSeparated(modelApi.model.definitions.errors.asApiConstants()) { error ->
                    val (errorName, errorCode) = error.getValue()
                    builder.staticClass(error.getRawName().toPascalCase()) {
                        builder.constant("Reference", errorName)
                        builder.constant("Code", errorCode)
                    }
                }
            }
        }
    }

    private class EscalationsWriter : ObjectWriter<CSharpWriter> {

        override fun addTo(builder: CSharpWriter, modelApi: BpmnModelApi) {
            builder.docComment("BPMN escalation definitions with name and code, as thrown and caught by the process.")
            builder.staticClass("Escalations") {
                builder.forEachSeparated(modelApi.model.definitions.escalations.asApiConstants()) { escalation ->
                    val (escalationName, escalationCode) = escalation.getValue()
                    builder.staticClass(escalation.getRawName().toPascalCase()) {
                        builder.constant("Reference", escalationName)
                        builder.constant("Code", escalationCode)
                    }
                }
            }
        }
    }

    private class CallActivitiesWriter : ObjectWriter<CSharpWriter> {

        override fun addTo(builder: CSharpWriter, modelApi: BpmnModelApi) {
            builder.docComment(
                """
                Call activities grouped by element. Each nested class exposes the called ProcessId plus the
                variable mappings passed into (Inputs) and returned from (Outputs) the called process.
                """.trimIndent(),
            )
            builder.staticClass("CallActivities") {
                builder.forEachSeparated(modelApi.model.callActivities.sortedBy { it.getRawName() }) { callActivity ->
                    builder.staticClass(callActivity.getRawName().toPascalCase()) {
                        builder.constant("ProcessId", callActivity.getValue())
                        writeMappings(builder, "Inputs", callActivity.inputMappings)
                        writeMappings(builder, "Outputs", callActivity.outputMappings)
                    }
                }
            }
        }

        /**
         * A mapping's `source` and `sourceExpression` are mutually exclusive; the absent one is left out
         * rather than emitted as `null`, matching how the Kotlin builder omits it.
         */
        private fun writeMappings(builder: CSharpWriter, className: String, mappings: List<CallActivityDefinition.Mapping>) {
            val withTarget = mappings.filter { !it.target.isNullOrBlank() }.sortedBy { it.target!!.toUpperSnakeCase() }
            if (withTarget.isEmpty()) return
            builder.line()
            builder.staticClass(className) {
                builder.forEachSeparated(withTarget) { mapping ->
                    builder.staticClass(mapping.target!!.toPascalCase()) {
                        builder.constant("Target", mapping.target!!)
                        mapping.source?.let { builder.constant("Source", it) }
                        mapping.sourceExpression?.let { builder.constant("SourceExpression", it) }
                    }
                }
            }
        }
    }

    private class VariablesWriter : ObjectWriter<CSharpWriter> {

        override fun addTo(builder: CSharpWriter, modelApi: BpmnModelApi) {
            builder.docComment(
                """
                Process variables grouped by the BPMN element that declares them.
                The JVM APIs encode direction in a wrapper type; without a C# runtime it is documented on each
                constant instead, so it still shows up in IntelliSense.
                """.trimIndent(),
            )
            val nodesWithVariables = modelApi.model.allFlowNodes
                .filter { it.variables.isNotEmpty() }
                .sortedBy { it.getRawName() }
            builder.staticClass("Variables") {
                builder.forEachSeparated(nodesWithVariables) { node ->
                    builder.staticClass(node.getRawName().toPascalCase()) {
                        writeNodeVariables(builder, node.variables.groupBy { it.getRawName() })
                    }
                }
            }
        }

        private fun writeNodeVariables(builder: CSharpWriter, variablesByName: Map<String, List<io.miragon.bpmn.domain.shared.VariableDefinition>>) {
            builder.forEachSeparated(variablesByName.keys.sorted()) { rawName ->
                val group = variablesByName.getValue(rawName)
                val subtype = VariableNameSubtype.chooseFor(group.map { it.direction }.toSet())
                builder.docComment(DIRECTION_DESCRIPTIONS.getValue(subtype))
                builder.constant(rawName.toPascalCase(), group.first().getValue())
            }
        }

        companion object {
            private val DIRECTION_DESCRIPTIONS = mapOf(
                VariableNameSubtype.INPUT to "Read by this element (input).",
                VariableNameSubtype.OUTPUT to "Written by this element (output).",
                VariableNameSubtype.IN_OUT to "Read and written by this element (in/out).",
            )
        }
    }
}
