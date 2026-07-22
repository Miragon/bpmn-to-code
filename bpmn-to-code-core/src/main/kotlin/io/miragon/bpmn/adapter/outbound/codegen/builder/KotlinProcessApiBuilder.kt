package io.miragon.bpmn.adapter.outbound.codegen.builder

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.CodeGenerationAdapter
import io.miragon.bpmn.adapter.outbound.codegen.navigation.NavGraph
import io.miragon.bpmn.adapter.outbound.codegen.navigation.NavGraph.NavNode
import io.miragon.bpmn.adapter.outbound.codegen.navigation.NavigationGraphFactory
import io.miragon.bpmn.adapter.outbound.codegen.writer.ObjectWriter
import io.miragon.bpmn.domain.BpmnModel
import io.miragon.bpmn.domain.BpmnModelApi
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.MergedBpmnModel
import io.miragon.bpmn.domain.MergedBpmnModel.VariantData
import io.miragon.bpmn.domain.shared.ApiObjectType
import io.miragon.bpmn.domain.shared.CallActivityDefinition
import io.miragon.bpmn.domain.shared.CallActivityMapping
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.SequenceFlowDefinition
import io.miragon.bpmn.domain.shared.VariableDefinition
import io.miragon.bpmn.domain.shared.VariableMapping
import io.miragon.bpmn.domain.utils.StringUtils.toCamelCase
import io.miragon.bpmn.domain.utils.StringUtils.toUpperSnakeCase

/**
 * Generates the type-safe API contract for a single BPMN process as a Kotlin object file.
 * References shared BPMN types (BpmnTimer, BpmnError, etc.) from the `bpmn-to-code-runtime` artifact.
 */
class KotlinProcessApiBuilder : CodeGenerationAdapter.AbstractProcessApiBuilder<TypeSpec.Builder>() {

    companion object {
        private const val RUNTIME_PACKAGE = "io.miragon.bpmn.runtime"
    }

    private val objectWriters: Map<ApiObjectType, ObjectWriter<TypeSpec.Builder>> = mapOf(
        ApiObjectType.PROCESS_ID to ProcessIdWriter(),
        ApiObjectType.PROCESS_ENGINE to ProcessEngineWriter(),
        ApiObjectType.ELEMENTS to ElementsWriter(),
        ApiObjectType.CALL_ACTIVITIES to CallActivitiesWriter(),
        ApiObjectType.MESSAGES to MessagesWriter(),
        ApiObjectType.SERVICE_TASKS to ServiceTasksWriter(),
        ApiObjectType.TIMERS to TimersWriter(),
        ApiObjectType.ERRORS to ErrorsWriter(),
        ApiObjectType.ESCALATIONS to EscalationsWriter(),
        ApiObjectType.COMPENSATIONS to CompensationsWriter(),
        ApiObjectType.SIGNALS to SignalsWriter(),
        ApiObjectType.VARIABLES to VariablesWriter(),
        ApiObjectType.FLOWS to FlowsWriter(),
        ApiObjectType.RELATIONS to RelationsWriter(),
        ApiObjectType.VARIANTS to VariantsWriter(),
    )

    override fun buildApiFile(modelApi: BpmnModelApi): GeneratedApiFile {
        val objectName = modelApi.fileName()
        val unusedAnnotation = AnnotationSpec.builder(Suppress::class).addMember("%S", "unused").build()
        val rootObjectBuilder = TypeSpec.objectBuilder(objectName)
        val fileSpecBuilder = FileSpec.builder(modelApi.packagePath, objectName).addFileComment(autoGenComment)

        val relevantWriters = objectWriters.filter { it.value.shouldWrite(modelApi) }
        relevantWriters.forEach { (_, writer) -> writer.write(rootObjectBuilder, modelApi) }

        fileSpecBuilder.addType(rootObjectBuilder.build()).addAnnotation(unusedAnnotation)
        val fileSpec = fileSpecBuilder.build()

        val content = buildString { fileSpec.writeTo(this) }.replace("public ", "")

        return GeneratedApiFile(
            fileName = "$objectName.kt",
            packagePath = modelApi.packagePath,
            content = content,
            language = modelApi.outputLanguage,
            processId = modelApi.model.processId,
        )
    }

    private inner class ProcessIdWriter : ObjectWriter<TypeSpec.Builder> {

        override val objectType = ApiObjectType.PROCESS_ID
        override fun shouldWrite(modelApi: BpmnModelApi) = true

        override fun write(builder: TypeSpec.Builder, modelApi: BpmnModelApi) {
            val processIdClass = ClassName(RUNTIME_PACKAGE, "ProcessId")
            val idProperty = PropertySpec.builder("PROCESS_ID", processIdClass)
                .initializer("%T(%L)", processIdClass, stringLiteral(modelApi.model.processId))
                .build()
            builder.addProperty(idProperty)
        }
    }

    private class ProcessEngineWriter : ObjectWriter<TypeSpec.Builder> {

        override val objectType = ApiObjectType.PROCESS_ENGINE
        override fun shouldWrite(modelApi: BpmnModelApi) = true

        override fun write(builder: TypeSpec.Builder, modelApi: BpmnModelApi) {
            val bpmnEngineClass = ClassName(RUNTIME_PACKAGE, "BpmnEngine")
            val engineProperty = PropertySpec.builder("PROCESS_ENGINE", bpmnEngineClass)
                .initializer("%T.%L", bpmnEngineClass, modelApi.engine.name)
                .build()
            builder.addProperty(engineProperty)
        }
    }

    private inner class ElementsWriter : ObjectWriter<TypeSpec.Builder> {

        override val objectType = ApiObjectType.ELEMENTS
        override fun shouldWrite(modelApi: BpmnModelApi) = true

        override fun write(builder: TypeSpec.Builder, modelApi: BpmnModelApi) {
            val elementIdClass = ClassName(RUNTIME_PACKAGE, "ElementId")
            val elementsBuilder = TypeSpec.objectBuilder("Elements")
                .addKdoc(
                    "BPMN element ids as declared in the source model.\n" +
                        "Typically used in process-level tests or when searching for tasks.\n" +
                        "Worker runtime code rarely needs these."
                )
            modelApi.model.flowNodes.forEach { flowNode ->
                elementsBuilder.addProperty(createTypedAttribute(flowNode, elementIdClass))
            }
            builder.addType(elementsBuilder.build())
        }
    }

    private inner class FlowsWriter : ObjectWriter<TypeSpec.Builder> {

        override val objectType = ApiObjectType.FLOWS
        override fun shouldWrite(modelApi: BpmnModelApi): Boolean {
            return modelApi.model is BpmnModel && modelApi.model.sequenceFlows.isNotEmpty()
        }

        override fun write(builder: TypeSpec.Builder, modelApi: BpmnModelApi) {
            val flowsObject = buildFlowsObject(modelApi.model.sequenceFlows)
            builder.addType(flowsObject)
        }
    }

    private inner class RelationsWriter : ObjectWriter<TypeSpec.Builder> {

        override val objectType = ApiObjectType.RELATIONS
        override fun shouldWrite(modelApi: BpmnModelApi): Boolean {
            return modelApi.model is BpmnModel && modelApi.model.sequenceFlows.isNotEmpty()
        }

        override fun write(builder: TypeSpec.Builder, modelApi: BpmnModelApi) {
            val relationsObject = buildRelationsObject(modelApi.model.flowNodes)
            builder.addType(relationsObject)
        }
    }

    private inner class VariantsWriter : ObjectWriter<TypeSpec.Builder> {

        override val objectType = ApiObjectType.VARIANTS
        override fun shouldWrite(modelApi: BpmnModelApi) = modelApi.model is MergedBpmnModel

        override fun write(builder: TypeSpec.Builder, modelApi: BpmnModelApi) {
            val model = modelApi.model as? MergedBpmnModel ?: return
            val variantsBuilder = TypeSpec.objectBuilder("Variants")
            model.variants.forEach { variant ->
                val variantObject = buildVariantObject(variant)
                variantsBuilder.addType(variantObject)
            }
            builder.addType(variantsBuilder.build())
        }

        private fun buildVariantObject(variant: VariantData): TypeSpec {
            val variantName = variant.variantName.toCamelCase()
            val variantBuilder = TypeSpec.objectBuilder(variantName)
            if (variant.sequenceFlows.isNotEmpty()) {
                variantBuilder.addType(buildFlowsObject(variant.sequenceFlows))
                variantBuilder.addType(buildRelationsObject(variant.flowNodes))
            }
            return variantBuilder.build()
        }
    }

    private fun buildFlowsObject(sequenceFlows: List<SequenceFlowDefinition>): TypeSpec {
        val bpmnFlowClass = ClassName(RUNTIME_PACKAGE, "BpmnFlow")
        val flowsBuilder = TypeSpec.objectBuilder("Flows")
            .addKdoc(
                "Sequence flows between BPMN elements.\n" +
                    "Mainly useful for process-model tooling, tests, and AI-agent consumers reasoning about the process shape.\n" +
                    "Worker code typically does not need these."
            )
        sequenceFlows.forEach { flow ->
            val initStr = buildFlowInitializer(flow.id ?: "", flow.flowName, flow.sourceRef, flow.targetRef, flow.conditionExpression, flow.isDefault)
            flowsBuilder.addProperty(PropertySpec.builder(flow.getName(), bpmnFlowClass).initializer(initStr).build())
        }
        return flowsBuilder.build()
    }

    private fun buildFlowInitializer(id: String, name: String?, sourceRef: String, targetRef: String, condition: String?, isDefault: Boolean): CodeBlock {
        return CodeBlock.builder().apply {
            add("BpmnFlow(\n")
            indent()
            add("id = %S,\n", id)
            if (name != null) add("name = %S,\n", name)
            add("sourceRef = %S,\n", sourceRef)
            add("targetRef = %S,\n", targetRef)
            if (condition != null) add("condition = %L,\n", stringLiteral(condition))
            if (isDefault) add("isDefault = true,\n")
            unindent()
            add(")")
        }.build()
    }

    /**
     * Renders the process as a typed navigation graph: one nested object per element exposing its `id`,
     * `elementType` and display `name`, plus its reachable successors as named accessors. Boundary events and
     * subprocess continuations are plain successors; a subprocess's interior is its nested `Inner` scope.
     */
    private fun buildRelationsObject(flowNodes: List<FlowNodeDefinition>): TypeSpec {
        val graph = NavigationGraphFactory.build(flowNodes)
        val relationsBuilder = TypeSpec.objectBuilder("Relations")
            .addKdoc(
                "Typed navigation over the process flow.\n" +
                    "Each element is a node exposing its `id`, `elementType` and display `name`, plus the elements " +
                    "reachable from it as named properties — so a full path is verified by the compiler and offered " +
                    "by autocomplete. A subprocess's interior is its nested `Inner` scope.\n" +
                    "Intended for tooling, tests, and reasoning about the process shape."
            )
        addNavScope(relationsBuilder, graph)
        return relationsBuilder.build()
    }

    private fun addNavScope(builder: TypeSpec.Builder, graph: NavGraph) {
        graph.nodes.forEach { node -> builder.addProperty(navAccessor(node.propertyName, node.objectName)) }
        // A scope's `then()` yields its start event(s) — the same "reachable next" meaning as a node's then(),
        // so you enter the process (or a subprocess interior via `Inner`) the same way you step through it.
        val starts = graph.nodes.filter { it.isStart }
        if (starts.isNotEmpty()) {
            builder.addFunction(
                FunSpec.builder("then").returns(ClassName("", "Next")).addStatement("return %N", "Next").build()
            )
            val nextBuilder = TypeSpec.objectBuilder("Next")
            starts.forEach { node -> nextBuilder.addProperty(navAccessor(node.propertyName, node.objectName)) }
            builder.addType(nextBuilder.build())
        }
        graph.nodes.forEach { node -> builder.addType(buildNavNodeObject(node)) }
    }

    private fun buildNavNodeObject(node: NavNode): TypeSpec {
        val elementIdClass = ClassName(RUNTIME_PACKAGE, "ElementId")
        val stringClass = ClassName("kotlin", "String")
        val nodeBuilder = TypeSpec.objectBuilder(node.objectName)
        nodeBuilder.addProperty(PropertySpec.builder("id", elementIdClass).initializer("ElementId(%S)", node.id).build())
        nodeBuilder.addProperty(PropertySpec.builder("elementType", stringClass).initializer("%S", node.elementType).build())
        if (node.name != null) {
            nodeBuilder.addProperty(PropertySpec.builder("name", stringClass).initializer("%S", node.name).build())
        }
        if (node.calledProcessId != null) {
            val processIdClass = ClassName(RUNTIME_PACKAGE, "ProcessId")
            nodeBuilder.addProperty(
                PropertySpec.builder("calledProcess", processIdClass).initializer("ProcessId(%S)", node.calledProcessId).build()
            )
        }
        // Transitions live behind `then()` in a nested `Next` holder — so the node itself carries only metadata,
        // and `then()` autocompletes exactly the reachable next steps. Terminal nodes have no `then()`.
        if (node.successors.isNotEmpty()) {
            nodeBuilder.addFunction(
                FunSpec.builder("then").returns(ClassName("", "Next")).addStatement("return %N", "Next").build()
            )
            val nextBuilder = TypeSpec.objectBuilder("Next")
            node.successors.forEach { edge -> nextBuilder.addProperty(navAccessor(edge.propertyName, edge.objectName)) }
            nodeBuilder.addType(nextBuilder.build())
        }
        node.inner?.let { inner ->
            val innerBuilder = TypeSpec.objectBuilder("Inner")
            addNavScope(innerBuilder, inner)
            nodeBuilder.addType(innerBuilder.build())
        }
        return nodeBuilder.build()
    }

    /** A lazy `val <name> get() = <Object>` accessor — lazy so forward references and loops (cycles) resolve. */
    private fun navAccessor(propertyName: String, objectName: String): PropertySpec {
        return PropertySpec.builder(propertyName, ClassName("", objectName))
            .getter(FunSpec.getterBuilder().addStatement("return %N", objectName).build())
            .build()
    }

    private inner class CallActivitiesWriter : ObjectWriter<TypeSpec.Builder> {

        override val objectType = ApiObjectType.CALL_ACTIVITIES
        override fun shouldWrite(modelApi: BpmnModelApi) = modelApi.model.callActivities.isNotEmpty()

        override fun write(builder: TypeSpec.Builder, modelApi: BpmnModelApi) {
            val callActivitiesBuilder = TypeSpec.objectBuilder("CallActivities")
                .addKdoc(
                    "Call activities grouped by element. Each nested object exposes the called `PROCESS_ID` plus " +
                        "the variable mappings passed into (`Inputs`) and returned from (`Outputs`) the called process.\n"
                )
            modelApi.model.callActivities
                .sortedBy { it.getRawName() }
                .forEach { callActivity -> callActivitiesBuilder.addType(buildCallActivityObject(callActivity)) }
            builder.addType(callActivitiesBuilder.build())
        }

        private fun buildCallActivityObject(callActivity: CallActivityDefinition): TypeSpec {
            val processIdClass = ClassName(RUNTIME_PACKAGE, "ProcessId")
            val objectBuilder = TypeSpec.objectBuilder(callActivity.getRawName().toCamelCase())
            objectBuilder.addProperty(
                PropertySpec.builder("PROCESS_ID", processIdClass)
                    .initializer("%T(%L)", processIdClass, stringLiteral(callActivity.getValue()))
                    .build()
            )
            buildMappingsObject("Inputs", callActivity.inputMappings)?.let { objectBuilder.addType(it) }
            buildMappingsObject("Outputs", callActivity.outputMappings)?.let { objectBuilder.addType(it) }
            return objectBuilder.build()
        }

        private fun buildMappingsObject(objectName: String, mappings: List<CallActivityMapping>): TypeSpec? {
            val withTarget = mappings
                .filter { !it.target.isNullOrBlank() }
                .sortedBy { it.target!!.toUpperSnakeCase() }
            if (withTarget.isEmpty()) return null
            val mappingClass = ClassName(RUNTIME_PACKAGE, "InputOutputMapping")
            val mappingsBuilder = TypeSpec.objectBuilder(objectName)
            withTarget.forEach { mapping -> mappingsBuilder.addProperty(buildMappingProperty(mapping, mappingClass)) }
            return mappingsBuilder.build()
        }

        private fun buildMappingProperty(mapping: CallActivityMapping, mappingClass: ClassName): PropertySpec {
            val target = mapping.target!!
            val args = CodeBlock.builder().add("target = %L", stringLiteral(target))
            if (mapping.source != null) args.add(", source = %L", stringLiteral(mapping.source))
            if (mapping.sourceExpression != null) args.add(", sourceExpression = %L", stringLiteral(mapping.sourceExpression))
            return PropertySpec.builder(target.toUpperSnakeCase(), mappingClass)
                .initializer("%T(%L)", mappingClass, args.build())
                .build()
        }
    }

    private inner class MessagesWriter : ObjectWriter<TypeSpec.Builder> {

        override val objectType = ApiObjectType.MESSAGES
        override fun shouldWrite(modelApi: BpmnModelApi) = modelApi.model.messages.isNotEmpty()

        override fun write(builder: TypeSpec.Builder, modelApi: BpmnModelApi) {
            val messageNameClass = ClassName(RUNTIME_PACKAGE, "MessageName")
            val messagesBuilder = TypeSpec.objectBuilder("Messages")
                .addKdoc("BPMN message names used to correlate messages to running process instances.")
            modelApi.model.messages.forEach { message ->
                messagesBuilder.addProperty(createTypedAttribute(message, messageNameClass))
            }
            builder.addType(messagesBuilder.build())
        }
    }

    /**
     * `ServiceTasks` intentionally emits `const val String` rather than a typed wrapper.
     * Its primary call site is `@JobWorker(type = ServiceTasks.X)` — Kotlin annotation arguments
     * require compile-time constants, which rules out `@JvmInline value class` instances.
     */
    private inner class ServiceTasksWriter : ObjectWriter<TypeSpec.Builder> {

        override val objectType = ApiObjectType.SERVICE_TASKS
        override fun shouldWrite(modelApi: BpmnModelApi) = modelApi.model.serviceTasks.any { it.getRawName().isNotEmpty() }

        override fun write(builder: TypeSpec.Builder, modelApi: BpmnModelApi) {
            val tasksBuilder = TypeSpec.objectBuilder("ServiceTasks")
                .addKdoc(
                    "Job worker task types used in `@JobWorker(type = ServiceTasks.X)` annotations.\n" +
                        "Kept as `const val String` because annotation arguments must be compile-time constants."
                )
            modelApi.model.serviceTasks
                .filter { it.getRawName().isNotEmpty() }
                .forEach { task -> tasksBuilder.addProperty(createAttribute(task)) }
            builder.addType(tasksBuilder.build())
        }
    }

    private inner class SignalsWriter : ObjectWriter<TypeSpec.Builder> {

        override val objectType = ApiObjectType.SIGNALS
        override fun shouldWrite(modelApi: BpmnModelApi) = modelApi.model.signals.isNotEmpty()

        override fun write(builder: TypeSpec.Builder, modelApi: BpmnModelApi) {
            val signalNameClass = ClassName(RUNTIME_PACKAGE, "SignalName")
            val signalsBuilder = TypeSpec.objectBuilder("Signals")
            modelApi.model.signals.forEach { signal ->
                signalsBuilder.addProperty(createTypedAttribute(signal, signalNameClass))
            }
            builder.addType(signalsBuilder.build())
        }
    }

    private inner class VariablesWriter : ObjectWriter<TypeSpec.Builder> {

        override val objectType = ApiObjectType.VARIABLES
        override fun shouldWrite(modelApi: BpmnModelApi) = modelApi.model.variables.isNotEmpty()

        override fun write(builder: TypeSpec.Builder, modelApi: BpmnModelApi) {
            val variableNameClass = ClassName(RUNTIME_PACKAGE, "VariableName")
            val variablesBuilder = TypeSpec.objectBuilder("Variables")
                .addKdoc(
                    "Process variables grouped by the BPMN element that declares them.\n" +
                        "Direction is encoded in each variable's wrapper type: `VariableName.Input`, `VariableName.Output`, or `VariableName.InOut` when the variable is both read and written by the same element.\n" +
                        "Consumer APIs that take a specific subtype (e.g. `fun setOutput(v: VariableName.Output)`) get compile-time direction enforcement."
                )
            val nodesWithVariables = modelApi.model.flowNodes
                .filter { it.variables.isNotEmpty() }
                .sortedBy { it.getRawName() }
            for (node in nodesWithVariables) {
                val objectName = node.getRawName().toCamelCase()
                val nodeVarsBuilder = TypeSpec.objectBuilder(objectName)
                val variablesByName = node.variables.groupBy { it.getRawName() }
                val sortedNames = variablesByName.keys.sorted()
                for (rawName in sortedNames) {
                    val group = variablesByName.getValue(rawName)
                    val directions = group.map { it.direction }.toSet()
                    val subtype = VariableNameSubtype.chooseFor(directions)
                    nodeVarsBuilder.addProperty(createDirectionalAttribute(group.first(), subtype, variableNameClass))
                }
                variablesBuilder.addType(nodeVarsBuilder.build())
            }
            builder.addType(variablesBuilder.build())
        }

        private fun createDirectionalAttribute(variable: VariableDefinition, subtype: VariableNameSubtype, wrapperClass: ClassName): PropertySpec {
            val subtypeClass = wrapperClass.nestedClass(subtype.simpleName)
            return PropertySpec.builder(variable.getName(), subtypeClass)
                .initializer("%T(%L)", subtypeClass, stringLiteral(variable.getValue()))
                .build()
        }
    }

    private class ErrorsWriter : ObjectWriter<TypeSpec.Builder> {

        override val objectType = ApiObjectType.ERRORS
        override fun shouldWrite(modelApi: BpmnModelApi): Boolean = modelApi.model.errors.isNotEmpty()

        override fun write(builder: TypeSpec.Builder, modelApi: BpmnModelApi) {
            val bpmnErrorClass = ClassName(RUNTIME_PACKAGE, "BpmnError")
            val errorsBuilder = TypeSpec.objectBuilder("Errors")
            modelApi.model.errors.forEach {
                val (errorName, errorCode) = it.getValue()
                val instanceBuilder = PropertySpec.builder(it.getName(), bpmnErrorClass)
                val variable = instanceBuilder.initializer("BpmnError(\"$errorName\", \"$errorCode\")")
                errorsBuilder.addProperty(variable.build())
            }
            builder.addType(errorsBuilder.build())
        }
    }

    private class EscalationsWriter : ObjectWriter<TypeSpec.Builder> {

        override val objectType = ApiObjectType.ESCALATIONS
        override fun shouldWrite(modelApi: BpmnModelApi): Boolean = modelApi.model.escalations.isNotEmpty()

        override fun write(builder: TypeSpec.Builder, modelApi: BpmnModelApi) {
            val bpmnEscalationClass = ClassName(RUNTIME_PACKAGE, "BpmnEscalation")
            val escalationsBuilder = TypeSpec.objectBuilder("Escalations")
            modelApi.model.escalations.forEach {
                val (escalationName, escalationCode) = it.getValue()
                val instanceBuilder = PropertySpec.builder(it.getName(), bpmnEscalationClass)
                val variable = instanceBuilder.initializer("BpmnEscalation(\"$escalationName\", \"$escalationCode\")")
                escalationsBuilder.addProperty(variable.build())
            }
            builder.addType(escalationsBuilder.build())
        }
    }

    private inner class CompensationsWriter : ObjectWriter<TypeSpec.Builder> {

        override val objectType = ApiObjectType.COMPENSATIONS
        override fun shouldWrite(modelApi: BpmnModelApi) = modelApi.model.compensations.isNotEmpty()

        override fun write(builder: TypeSpec.Builder, modelApi: BpmnModelApi) {
            val elementIdClass = ClassName(RUNTIME_PACKAGE, "ElementId")
            val compensationsBuilder = TypeSpec.objectBuilder("Compensations")
            modelApi.model.compensations.forEach { compensation ->
                compensationsBuilder.addProperty(createTypedAttribute(compensation, elementIdClass))
            }
            builder.addType(compensationsBuilder.build())
        }
    }

    private inner class TimersWriter : ObjectWriter<TypeSpec.Builder> {

        override val objectType = ApiObjectType.TIMERS
        override fun shouldWrite(modelApi: BpmnModelApi): Boolean = modelApi.model.timers.isNotEmpty()

        override fun write(builder: TypeSpec.Builder, modelApi: BpmnModelApi) {
            val bpmnTimerClass = ClassName(RUNTIME_PACKAGE, "BpmnTimer")
            val timersBuilder = TypeSpec.objectBuilder("Timers")
            modelApi.model.timers.forEach { timer ->
                val (timerType, timerValue) = timer.getValue()
                val instanceBuilder = PropertySpec.builder(timer.getName(), bpmnTimerClass)
                val variable = instanceBuilder.initializer("%T(%S, %L)", bpmnTimerClass, timerType, stringLiteral(timerValue))
                timersBuilder.addProperty(variable.build())
            }
            builder.addType(timersBuilder.build())
        }
    }

    private fun createAttribute(variable: VariableMapping<String>): PropertySpec {
        return PropertySpec.builder(variable.getName(), String::class)
            .addModifiers(KModifier.CONST)
            .initializer("%L", stringLiteral(variable.getValue()))
            .build()
    }

    private fun createTypedAttribute(variable: VariableMapping<String>, wrapperClass: ClassName): PropertySpec {
        return PropertySpec.builder(variable.getName(), wrapperClass)
            .initializer("%T(%L)", wrapperClass, stringLiteral(variable.getValue()))
            .build()
    }

    private fun stringLiteral(value: String): CodeBlock {
        return if (value.contains("\${")) {
            CodeBlock.of("\$\$\"\"\"%L\"\"\"", value)
        } else {
            CodeBlock.of("%S", value)
        }
    }
}
