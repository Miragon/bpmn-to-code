package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.CodeGenerationAdapter
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.SharedDefinitionsApi

/**
 * Generates one Kotlin object per kind of engine-global identifier, shared by all Process APIs of a run.
 */
internal class KotlinSharedDefinitionsBuilder : CodeGenerationAdapter.AbstractSharedDefinitionsBuilder() {

    companion object {
        private const val RUNTIME_PACKAGE = "io.miragon.bpmn.runtime"
    }

    override fun buildApiFiles(api: SharedDefinitionsApi): List<GeneratedApiFile> = with(api.definitions) {
        listOfNotNull(
            serviceTasks.ifNotEmpty {
                KotlinSharedDefinitionHolder(
                    type = SharedDefinitionType.SERVICE_TASKS,
                    kdoc = "Job worker task types used in `@JobWorker(type = ServiceTasks.X)` annotations.\n" +
                        "Kept as `const val String` because annotation arguments must be compile-time constants.",
                ).withConstants(it)
            },
            messages.ifNotEmpty {
                KotlinSharedDefinitionHolder(
                    type = SharedDefinitionType.MESSAGES,
                    kdoc = "BPMN message names used to correlate messages to running process instances.",
                ).withNames(it, runtimeClass("MessageName"))
            },
            signals.ifNotEmpty {
                KotlinSharedDefinitionHolder(
                    type = SharedDefinitionType.SIGNALS,
                    kdoc = "BPMN signal names broadcast and caught by signal events.",
                ).withNames(it, runtimeClass("SignalName"))
            },
            errors.ifNotEmpty {
                KotlinSharedDefinitionHolder(
                    type = SharedDefinitionType.ERRORS,
                    kdoc = "BPMN error definitions with name and code, as thrown and caught by the processes.",
                ).withNamesAndCodes(it, runtimeClass("BpmnErrorDefinition"))
            },
            escalations.ifNotEmpty {
                KotlinSharedDefinitionHolder(
                    type = SharedDefinitionType.ESCALATIONS,
                    kdoc = "BPMN escalation definitions with name and code, as thrown and caught by the processes.",
                ).withNamesAndCodes(it, runtimeClass("BpmnEscalationDefinition"))
            },
        ).map { toFile(it, api) }
    }

    private fun runtimeClass(name: String): ClassName = ClassName(RUNTIME_PACKAGE, name)

    private fun toFile(type: TypeSpec, api: SharedDefinitionsApi): GeneratedApiFile {
        val objectName = requireNotNull(type.name)
        val unusedAnnotation = AnnotationSpec.builder(Suppress::class).addMember("%S", "unused").build()
        val fileSpec = FileSpec.builder(api.packagePath, objectName)
            .addFileComment(autoGenComment).addType(type).addAnnotation(unusedAnnotation).build()
        return GeneratedApiFile(
            fileName = "$objectName.kt",
            packagePath = api.packagePath,
            content = buildString { fileSpec.writeTo(this) }.withoutPublicModifiers(),
            language = api.outputLanguage,
            processId = null,
        )
    }

    private fun <T> List<T>.ifNotEmpty(build: (List<T>) -> TypeSpec): TypeSpec? = if (isEmpty()) null else build(this)
}
