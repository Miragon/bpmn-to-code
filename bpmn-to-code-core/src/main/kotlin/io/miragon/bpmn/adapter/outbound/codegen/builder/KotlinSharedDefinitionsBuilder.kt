package io.miragon.bpmn.adapter.outbound.codegen.builder

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.CodeGenerationAdapter
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.SharedDefinitionsApi
import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.shared.ServiceTaskDefinition
import io.miragon.bpmn.domain.shared.VariableMapping

/**
 * Generates one Kotlin object per kind of engine-global identifier, shared by all Process APIs of a run.
 */
internal class KotlinSharedDefinitionsBuilder : CodeGenerationAdapter.AbstractSharedDefinitionsBuilder() {

    companion object {
        private const val RUNTIME_PACKAGE = "io.miragon.bpmn.runtime"
    }

    override fun buildApiFiles(api: SharedDefinitionsApi): List<GeneratedApiFile> {
        val definitions = api.definitions
        return listOfNotNull(
            serviceTasks(definitions.serviceTasks),
            messages(definitions.messages),
            signals(definitions.signals),
            errors(definitions.errors),
            escalations(definitions.escalations),
        ).map { toFile(it, api) }
    }

    /**
     * `ServiceTasks` intentionally emits `const val String` rather than a typed wrapper.
     * Its primary call site is `@JobWorker(type = ServiceTasks.X)` — Kotlin annotation arguments
     * require compile-time constants, which rules out `@JvmInline value class` instances.
     */
    private fun serviceTasks(serviceTasks: List<ServiceTaskDefinition>): TypeSpec? = serviceTasks.ifNotEmpty {
        val tasksBuilder = TypeSpec.objectBuilder("ServiceTasks")
            .addKdoc(
                "Job worker task types used in `@JobWorker(type = ServiceTasks.X)` annotations.\n" +
                    "Kept as `const val String` because annotation arguments must be compile-time constants.",
            )
        serviceTasks.forEach { task -> tasksBuilder.addProperty(createConstant(task)) }
        tasksBuilder.build()
    }

    private fun messages(messages: List<RootElementDefinition.Message>): TypeSpec? = messages.ifNotEmpty {
        val messageNameClass = ClassName(RUNTIME_PACKAGE, "MessageName")
        val messagesBuilder = TypeSpec.objectBuilder("Messages")
            .addKdoc("BPMN message names used to correlate messages to running process instances.")
        messages.forEach { message -> messagesBuilder.addProperty(createTypedAttribute(message, messageNameClass)) }
        messagesBuilder.build()
    }

    private fun signals(signals: List<RootElementDefinition.Signal>): TypeSpec? = signals.ifNotEmpty {
        val signalNameClass = ClassName(RUNTIME_PACKAGE, "SignalName")
        val signalsBuilder = TypeSpec.objectBuilder("Signals")
            .addKdoc("BPMN signal names broadcast and caught by signal events.")
        signals.forEach { signal -> signalsBuilder.addProperty(createTypedAttribute(signal, signalNameClass)) }
        signalsBuilder.build()
    }

    private fun errors(errors: List<RootElementDefinition.Error>): TypeSpec? = errors.ifNotEmpty {
        val bpmnErrorClass = ClassName(RUNTIME_PACKAGE, "BpmnError")
        val errorsBuilder = TypeSpec.objectBuilder("Errors")
            .addKdoc("BPMN error definitions with name and code, as thrown and caught by the processes.")
        errors.forEach { errorsBuilder.addProperty(createNameAndCodeAttribute(it, bpmnErrorClass)) }
        errorsBuilder.build()
    }

    private fun escalations(escalations: List<RootElementDefinition.Escalation>): TypeSpec? = escalations.ifNotEmpty {
        val bpmnEscalationClass = ClassName(RUNTIME_PACKAGE, "BpmnEscalation")
        val escalationsBuilder = TypeSpec.objectBuilder("Escalations")
            .addKdoc("BPMN escalation definitions with name and code, as thrown and caught by the processes.")
        escalations.forEach { escalationsBuilder.addProperty(createNameAndCodeAttribute(it, bpmnEscalationClass)) }
        escalationsBuilder.build()
    }

    private fun toFile(type: TypeSpec, api: SharedDefinitionsApi): GeneratedApiFile {
        val objectName = requireNotNull(type.name)
        val unusedAnnotation = AnnotationSpec.builder(Suppress::class).addMember("%S", "unused").build()
        val fileSpec = FileSpec.builder(api.packagePath, objectName)
            .addFileComment(autoGenComment)
            .addType(type)
            .addAnnotation(unusedAnnotation)
            .build()
        return GeneratedApiFile(
            fileName = "$objectName.kt",
            packagePath = api.packagePath,
            content = buildString { fileSpec.writeTo(this) }.replace("public ", ""),
            language = api.outputLanguage,
            processId = null,
        )
    }

    private fun createConstant(variable: VariableMapping<String>): PropertySpec = PropertySpec.builder(variable.getName(), String::class)
        .addModifiers(KModifier.CONST)
        .initializer("%L", kotlinStringLiteral(variable.getValue()))
        .build()

    private fun createTypedAttribute(variable: VariableMapping<String>, wrapperClass: ClassName): PropertySpec = PropertySpec.builder(variable.getName(), wrapperClass)
        .initializer("%T(%L)", wrapperClass, kotlinStringLiteral(variable.getValue()))
        .build()

    private fun createNameAndCodeAttribute(variable: VariableMapping<Pair<String, String>>, wrapperClass: ClassName): PropertySpec {
        val (name, code) = variable.getValue()
        return PropertySpec.builder(variable.getName(), wrapperClass)
            .initializer("%T(%S, %S)", wrapperClass, name, code)
            .build()
    }

    private fun <T> List<T>.ifNotEmpty(build: () -> TypeSpec): TypeSpec? = if (isEmpty()) null else build()
}
