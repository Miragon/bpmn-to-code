package io.miragon.bpmn.adapter.outbound.codegen.builder

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.JavaFile
import com.palantir.javapoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.CodeGenerationAdapter
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.SharedDefinitionsApi
import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.shared.ServiceTaskDefinition
import io.miragon.bpmn.domain.shared.VariableMapping
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

/**
 * Generates one Java class per kind of engine-global identifier, shared by all Process APIs of a run.
 */
internal class JavaSharedDefinitionsBuilder : CodeGenerationAdapter.AbstractSharedDefinitionsBuilder() {

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

    private fun serviceTasks(serviceTasks: List<ServiceTaskDefinition>): TypeSpec? = serviceTasks.ifNotEmpty {
        val tasksBuilder = TypeSpec.classBuilder("ServiceTasks").addModifiers(PUBLIC, FINAL)
            .addJavadoc(
                "Job worker task types used in {@code @JobWorker(type = ServiceTasks.X)} annotations.\n" +
                    "Kept as {@code public static final String} because annotation arguments must be compile-time constants.\n",
            )
        serviceTasks.forEach { task -> tasksBuilder.addField(createConstant(task)) }
        tasksBuilder.build()
    }

    private fun messages(messages: List<RootElementDefinition.Message>): TypeSpec? = messages.ifNotEmpty {
        val messageNameClass = ClassName.get(RUNTIME_PACKAGE, "MessageName")
        val messagesBuilder = TypeSpec.classBuilder("Messages").addModifiers(PUBLIC, FINAL)
            .addJavadoc("BPMN message names used to correlate messages to running process instances.\n")
        messages.forEach { message -> messagesBuilder.addField(createTypedAttribute(message, messageNameClass)) }
        messagesBuilder.build()
    }

    private fun signals(signals: List<RootElementDefinition.Signal>): TypeSpec? = signals.ifNotEmpty {
        val signalNameClass = ClassName.get(RUNTIME_PACKAGE, "SignalName")
        val signalsBuilder = TypeSpec.classBuilder("Signals").addModifiers(PUBLIC, FINAL)
            .addJavadoc("BPMN signal names broadcast and caught by signal events.\n")
        signals.forEach { signal -> signalsBuilder.addField(createTypedAttribute(signal, signalNameClass)) }
        signalsBuilder.build()
    }

    private fun errors(errors: List<RootElementDefinition.Error>): TypeSpec? = errors.ifNotEmpty {
        val bpmnErrorClass = ClassName.get(RUNTIME_PACKAGE, "BpmnError")
        val errorsBuilder = TypeSpec.classBuilder("Errors").addModifiers(PUBLIC, FINAL)
            .addJavadoc("BPMN error definitions with name and code, as thrown and caught by the processes.\n")
        errors.forEach { errorsBuilder.addField(createNameAndCodeAttribute(it, bpmnErrorClass)) }
        errorsBuilder.build()
    }

    private fun escalations(escalations: List<RootElementDefinition.Escalation>): TypeSpec? = escalations.ifNotEmpty {
        val bpmnEscalationClass = ClassName.get(RUNTIME_PACKAGE, "BpmnEscalation")
        val escalationsBuilder = TypeSpec.classBuilder("Escalations").addModifiers(PUBLIC, FINAL)
            .addJavadoc("BPMN escalation definitions with name and code, as thrown and caught by the processes.\n")
        escalations.forEach { escalationsBuilder.addField(createNameAndCodeAttribute(it, bpmnEscalationClass)) }
        escalationsBuilder.build()
    }

    private fun toFile(type: TypeSpec, api: SharedDefinitionsApi): GeneratedApiFile {
        val javaFile = JavaFile.builder(api.packagePath, type).addFileComment(autoGenComment).build()
        return GeneratedApiFile(
            fileName = "${type.name()}.java",
            packagePath = api.packagePath,
            content = buildString { javaFile.writeTo(this) },
            language = api.outputLanguage,
            processId = null,
        )
    }

    private fun createConstant(variable: VariableMapping<String>): FieldSpec = FieldSpec.builder(String::class.java, variable.getName())
        .addModifiers(PUBLIC, STATIC, FINAL)
        .initializer("\$S", variable.getValue())
        .build()

    private fun createTypedAttribute(variable: VariableMapping<String>, wrapperClass: ClassName): FieldSpec = FieldSpec.builder(wrapperClass, variable.getName())
        .addModifiers(PUBLIC, STATIC, FINAL)
        .initializer("new \$T(\$S)", wrapperClass, variable.getValue())
        .build()

    private fun createNameAndCodeAttribute(variable: VariableMapping<Pair<String, String>>, wrapperClass: ClassName): FieldSpec {
        val (name, code) = variable.getValue()
        return FieldSpec.builder(wrapperClass, variable.getName())
            .addModifiers(PUBLIC, STATIC, FINAL)
            .initializer("new \$T(\$S, \$S)", wrapperClass, name, code)
            .build()
    }

    private fun <T> List<T>.ifNotEmpty(build: () -> TypeSpec): TypeSpec? = if (isEmpty()) null else build()
}
