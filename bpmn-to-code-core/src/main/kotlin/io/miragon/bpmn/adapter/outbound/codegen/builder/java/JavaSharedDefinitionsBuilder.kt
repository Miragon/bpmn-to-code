package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.JavaFile
import com.palantir.javapoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.CodeGenerationAdapter
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.SharedDefinitionsApi

/**
 * Generates one Java class per kind of engine-global identifier, shared by all Process APIs of a run.
 */
internal class JavaSharedDefinitionsBuilder : CodeGenerationAdapter.AbstractSharedDefinitionsBuilder() {

    companion object {
        private const val RUNTIME_PACKAGE = "io.miragon.bpmn.runtime"
    }

    override fun buildApiFiles(api: SharedDefinitionsApi): List<GeneratedApiFile> = with(api.definitions) {
        listOfNotNull(
            serviceTasks.ifNotEmpty {
                JavaSharedDefinitionHolder(
                    type = SharedDefinitionType.SERVICE_TASKS,
                    javadoc = "Job worker task types used in {@code @JobWorker(type = ServiceTasks.X)} annotations.\n" +
                        "Kept as {@code public static final String} because annotation arguments must be compile-time constants.\n",
                ).withConstants(it)
            },
            messages.ifNotEmpty {
                JavaSharedDefinitionHolder(
                    type = SharedDefinitionType.MESSAGES,
                    javadoc = "BPMN message names used to correlate messages to running process instances.\n",
                ).withNames(it, runtimeClass("MessageName"))
            },
            signals.ifNotEmpty {
                JavaSharedDefinitionHolder(
                    type = SharedDefinitionType.SIGNALS,
                    javadoc = "BPMN signal names broadcast and caught by signal events.\n",
                ).withNames(it, runtimeClass("SignalName"))
            },
            errors.ifNotEmpty {
                JavaSharedDefinitionHolder(
                    type = SharedDefinitionType.ERRORS,
                    javadoc = "BPMN error definitions with name and code, as thrown and caught by the processes.\n",
                ).withNamesAndCodes(it, runtimeClass("BpmnErrorDefinition"))
            },
            escalations.ifNotEmpty {
                JavaSharedDefinitionHolder(
                    type = SharedDefinitionType.ESCALATIONS,
                    javadoc = "BPMN escalation definitions with name and code, as thrown and caught by the processes.\n",
                ).withNamesAndCodes(it, runtimeClass("BpmnEscalationDefinition"))
            },
        ).map { toFile(it, api) }
    }

    private fun runtimeClass(name: String): ClassName = ClassName.get(RUNTIME_PACKAGE, name)

    private fun toFile(type: TypeSpec, api: SharedDefinitionsApi): GeneratedApiFile {
        val javaFile = JavaFile.builder(api.packagePath, type).skipJavaLangImports(true).addFileComment(autoGenComment).build()
        return GeneratedApiFile(
            fileName = "${type.name()}.java",
            packagePath = api.packagePath,
            content = buildString { javaFile.writeTo(this) },
            language = api.outputLanguage,
            processId = null,
        )
    }

    private fun <T> List<T>.ifNotEmpty(build: (List<T>) -> TypeSpec): TypeSpec? = if (isEmpty()) null else build(this)
}
