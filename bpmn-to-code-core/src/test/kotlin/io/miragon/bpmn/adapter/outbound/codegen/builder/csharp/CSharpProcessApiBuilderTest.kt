package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.assertMatchesGolden
import io.miragon.bpmn.adapter.outbound.codegen.CodeGenerationAdapter
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpSyntax.pascalCase
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.GatewayKind
import io.miragon.bpmn.domain.shared.MessageReference
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.shared.SequenceFlowDefinition
import io.miragon.bpmn.domain.shared.TimerType
import io.miragon.bpmn.domain.shared.VariableDefinition
import io.miragon.bpmn.domain.shared.VariableDirection
import io.miragon.bpmn.domain.testBikeLeasingModel
import io.miragon.bpmn.domain.testCancelBikeOrderModel
import io.miragon.bpmn.domain.testProcessModel
import io.miragon.bpmn.domain.testProcessModelApi
import io.miragon.bpmn.domain.withId
import io.miragon.bpmn.runtime.BpmnElementType
import io.miragon.bpmn.runtime.BpmnEngine
import io.miragon.bpmn.runtime.BpmnEventType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import io.miragon.bpmn.runtime.TimerType as RuntimeTimerType

class CSharpProcessApiBuilderTest {

    private val underTest = CodeGenerationAdapter()

    private val runtimePackageSource = File(requireNotNull(System.getProperty("csharp.runtime.project")))
        .listFiles { file -> file.extension == "cs" }.orEmpty()
        .sortedBy { it.name }
        .joinToString("\n") { it.readText() }

    @Test
    fun `generateCode generates correct process API file`() {
        // given: the bike-leasing model, which covers every implementation kind of Camunda 7
        val modelApi = testProcessModelApi(
            packagePath = "de.emaarco.example",
            language = OutputLanguage.CSHARP,
            model = testBikeLeasingModel(),
        )

        // when: we build the process API file
        val result = underTest.generateCode(modelApi).single()

        // then: a single model file is returned at the root package
        assertThat(result.fileName).isEqualTo("${modelApi.fileName()}.cs")
        assertThat(result.packagePath).isEqualTo("de.emaarco.example")
        assertThat(result.language).isEqualTo(OutputLanguage.CSHARP)

        assertMatchesGolden(result.content, "/api/BikeLeasingProcessApiCsharp.txt")
    }

    @Test
    fun `generateCode leads the API name with the variant name of the model`() {
        // given: a model that declares a variant name
        val modelApi = testProcessModelApi(
            packagePath = "de.emaarco.example",
            language = OutputLanguage.CSHARP,
            model = testCancelBikeOrderModel(variantName = "retail"),
        )

        // when: we build the process API file
        val result = underTest.generateCode(modelApi).single()

        // then: the API carries the prefix, with the gateway's conditional and default flows named after their targets
        assertThat(result.fileName).isEqualTo("RetailCancelBikeOrderProcessApi.cs")
        assertMatchesGolden(result.content, "/api/PrefixedCancelBikeOrderProcessApiCsharp.txt")
        assertThat(result.content).contains("public static class RetailCancelBikeOrderProcessApi", "public const string ProcessId = \"cancelBikeOrder\";")
        assertThat(result.content).contains(
            $$"public global::Miragon.BpmnToCode.Runtime.SequenceFlows<GatewayCollectClarificationsNode> GatewayCollectClarifications => _gatewayCollectClarifications ??= global::Miragon.BpmnToCode.Runtime.SequenceFlows.Single(new(\"flow_cancellationNotPossibleToCollectClarifications\"), FlowNodes.GatewayCollectClarifications, name: \"No\", conditionExpression: \"${!cancellationPossible}\");",
        )
        assertThat(result.content).contains(
            "public global::Miragon.BpmnToCode.Runtime.SequenceFlows<GatewayMergeReturnNode> GatewayMergeReturn => _gatewayMergeReturn ??= global::Miragon.BpmnToCode.Runtime.SequenceFlows.Single(new(\"flow_cancellationPossibleToMergeReturn\"), FlowNodes.GatewayMergeReturn, name: \"Yes\", isDefault: true);",
        )
    }

    @Test
    fun `several flows to the same element share one SequenceFlows successor`() {
        // given: a gateway with two conditional flows that both lead to the same task
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Gateway(id = "split", kind = GatewayKind.EXCLUSIVE, outgoing = listOf("flow_small", "flow_vip")),
                FlowNodeDefinition.Unknown(id = "approve", incoming = listOf("flow_small", "flow_vip")),
            ),
            sequenceFlows = listOf(
                SequenceFlowDefinition(
                    id = "flow_small",
                    sourceRef = "split",
                    targetRef = "approve",
                    conditionExpression = "=amount < 100",
                ),
                SequenceFlowDefinition(
                    id = "flow_vip",
                    sourceRef = "split",
                    targetRef = "approve",
                    conditionExpression = "=customer.isVip",
                ),
            ),
        )

        // when
        val result = underTest.generateCode(csharpApi(model)).single()

        // then: one stable name for both flows, carried by one SequenceFlows
        assertThat(result.content).contains(
            "public global::Miragon.BpmnToCode.Runtime.SequenceFlows<ApproveNode> Approve => _approve ??= new(FlowNodes.Approve, new global::Miragon.BpmnToCode.Runtime.SequenceFlow<ApproveNode>[] {",
        )
    }

    @Test
    fun `file header marks the file as generated and enables nullable annotations`() {
        val result = underTest.generateCode(csharpApi(testBikeLeasingModel())).single()

        assertThat(result.content.lines().take(4)).containsExactly(
            "// <auto-generated/>",
            "// Generated by bpmn-to-code",
            "#nullable enable",
            "#pragma warning disable CS1591",
        )
    }

    @Test
    fun `refers to the runtime package by its global name instead of inlining its types`() {
        val result = underTest.generateCode(csharpApi(testBikeLeasingModel())).single()

        assertThat(result.content).contains(": global::Miragon.BpmnToCode.Runtime.IFlowNode")
        assertThat(result.content).doesNotContain("class Runtime", "public interface IFlowNode", " Runtime.")
    }

    @Test
    fun `runtime package enums mirror the Kotlin and Java runtime enums in PascalCase`() {
        val runtimeEnums = listOf(BpmnElementType.entries, BpmnEventType.entries, RuntimeTimerType.entries, BpmnEngine.entries)

        runtimeEnums.flatten().forEach { constant ->
            assertThat(runtimePackageSource).contains(" ${pascalCase(constant.name)},")
        }
    }

    @Test
    fun `variable name records keep the raw name as string representation in derived records`() {
        assertThat(runtimePackageSource).contains(
            """
            |public abstract record VariableName(string Value)
            |{
            |    public sealed override string ToString() => Value;
            """.trimMargin(),
        )
    }

    @Test
    fun `flat FlowNodes lists subprocess interior nodes as direct children with Start on the subprocess`() {
        val result = underTest.generateCode(csharpApi(testBikeLeasingModel())).single()

        assertThat(result.content).contains("        public sealed class StartEventCustomerEligibleNode : global::Miragon.BpmnToCode.Runtime.IEvent")
        assertThat(result.content).contains("public Interior Start { get; } = new();")
        assertThat(result.content).contains(
            "public sealed class SubProcessConcludeContractNode : global::Miragon.BpmnToCode.Runtime.IFlowNode, " +
                "global::Miragon.BpmnToCode.Runtime.ISubProcess<SubProcessConcludeContractNode.Interior, SubProcessConcludeContractNode.Successors>",
        )
        assertThat(result.content).contains("public StartEventCustomerEligibleNode StartEventCustomerEligible => FlowNodes.StartEventCustomerEligible;")
    }

    @Test
    fun `boundary event exposes its host and whether it interrupts`() {
        val result = underTest.generateCode(csharpApi(testBikeLeasingModel())).single()

        assertThat(result.content).contains("public SubProcessConcludeContractNode AttachedTo => FlowNodes.SubProcessConcludeContract;")
        assertThat(result.content).contains("public bool IsInterrupting => false;")
        assertThat(result.content).contains("public global::Miragon.BpmnToCode.Runtime.BpmnTimer Timer { get; } = new(global::Miragon.BpmnToCode.Runtime.TimerType.Duration, \"P7D\");")
    }

    @Test
    fun `node carries job type, variables and call-activity mappings`() {
        val result = underTest.generateCode(csharpApi(testBikeLeasingModel())).single()

        assertThat(result.content).contains("public string JobType { get; } = ServiceTasks.MiraveloOrderBike;")
        assertThat(result.content).contains("public global::Miragon.BpmnToCode.Runtime.VariableName.Input ApplicationId { get; } = new(ProcessVariables.ApplicationId);")
        assertThat(result.content).contains("public global::Miragon.BpmnToCode.Runtime.ProcessId CalledProcess { get; } = new(\"cancelBikeOrder\");")
        assertThat(result.content).contains("public global::Miragon.BpmnToCode.Runtime.MessageName Message { get; } = new(Messages.MiraveloLeasingRequestReceived);")
        assertThat(result.content).contains(
            "public global::Miragon.BpmnToCode.Runtime.BpmnErrorDefinition Error { get; } = new(Errors.MiraveloApplicationInvalid.Reference, Errors.MiraveloApplicationInvalid.Code);",
        )
    }

    @Test
    fun `node references a shared constant under the name its shared class gives it`() {
        // given: a message named exactly like the shared class, which renames the constant (CS0542)
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Event(
                    id = "onMessage",
                    shape = EventShape.START_EVENT,
                    eventDefinitions = listOf(EventDefinitionInstance.Message(MessageReference(messageRef = "Messages"))),
                ),
            ),
            messages = listOf(RootElementDefinition.Message(id = "Messages", name = "Messages")),
        )

        // when: we build the process API file
        val result = underTest.generateCode(csharpApi(model)).single()

        // then: the node points at the renamed constant
        assertThat(result.content).contains("public global::Miragon.BpmnToCode.Runtime.MessageName Message { get; } = new(Messages.Messages_);")
    }

    @Test
    fun `eager initializers never reference another node, so static initialisation cannot cycle`() {
        val result = underTest.generateCode(csharpApi(testBikeLeasingModel())).single()

        val eagerInitializers = result.content.lines().filter { it.contains("{ get; } = ") }
        assertThat(eagerInitializers).isNotEmpty()
        assertThat(eagerInitializers).noneMatch { it.contains("FlowNodes.") }
    }

    @Test
    fun `node is reached through a property of FlowNodes, its type carries a Node suffix`() {
        // given: a timer event whose id folds to the name of its Timer property
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Event(
                    id = "timer",
                    shape = EventShape.INTERMEDIATE_CATCH_EVENT,
                    eventDefinitions = listOf(EventDefinitionInstance.Timer(TimerType.DURATION, "PT1M")),
                ),
            ),
        )

        // when: we build the process API file
        val result = underTest.generateCode(csharpApi(model)).single()

        // then: the node is a property of FlowNodes, so no member has to give way to its type's name
        assertThat(result.content).contains("public static TimerNode Timer => TimerNode.Instance;")
        assertThat(result.content).contains("public sealed class TimerNode : global::Miragon.BpmnToCode.Runtime.IEvent, global::Miragon.BpmnToCode.Runtime.ITimerEvent")
        assertThat(result.content).contains("public global::Miragon.BpmnToCode.Runtime.BpmnTimer Timer { get; } = new(global::Miragon.BpmnToCode.Runtime.TimerType.Duration, \"PT1M\");")
    }

    @Test
    fun `a node type named like the property of another node gets a trailing underscore`() {
        // given: elements whose names differ only by a Node suffix
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "task", outgoing = listOf("flow_taskToTaskNode")),
                FlowNodeDefinition.Unknown(id = "taskNode", incoming = listOf("flow_taskToTaskNode")),
            ),
            sequenceFlows = listOf(SequenceFlowDefinition(id = "flow_taskToTaskNode", sourceRef = "task", targetRef = "taskNode")),
        )

        // when: we build the process API file
        val result = underTest.generateCode(csharpApi(model)).single()

        // then: the type of task gives way to the property of taskNode, which keeps its own type
        assertThat(result.content).contains("public static TaskNode_ Task => TaskNode_.Instance;")
        assertThat(result.content).contains("public static TaskNodeNode TaskNode => TaskNodeNode.Instance;")
        assertThat(result.content).contains("public global::Miragon.BpmnToCode.Runtime.SequenceFlows<TaskNodeNode> TaskNode => _taskNode ??= ")
    }

    @Test
    fun `namespace is the package path in PascalCase`() {
        val result = underTest.generateCode(csharpApi(testBikeLeasingModel())).single()

        assertThat(result.content).contains("namespace De.Emaarco.Example;")
        assertThat(result.packagePath).isEqualTo("de.emaarco.example")
    }

    @Test
    fun `node lists the variables it declares behind the variables facet`() {
        val result = underTest.generateCode(csharpApi(testBikeLeasingModel())).single()

        assertThat(result.content).contains(
            "public sealed class ServiceTaskOrderBikeNode : global::Miragon.BpmnToCode.Runtime.IFlowNode, " +
                "global::Miragon.BpmnToCode.Runtime.IHasSuccessors<ServiceTaskOrderBikeNode.Successors>, " +
                "global::Miragon.BpmnToCode.Runtime.IHasJobType, global::Miragon.BpmnToCode.Runtime.IHasVariables",
        )
        assertThat(result.content).contains("global::Miragon.BpmnToCode.Runtime.VariableDefinitions global::Miragon.BpmnToCode.Runtime.IHasVariables.Variables => Variables;")
        assertThat(result.content).contains("public sealed class NodeVariables : global::Miragon.BpmnToCode.Runtime.VariableDefinitions")
        assertThat(result.content).contains(
            "public override global::System.Collections.Generic.IReadOnlyList<global::Miragon.BpmnToCode.Runtime.VariableName> All => new global::Miragon.BpmnToCode.Runtime.VariableName[] { BikeId, BikeIds };",
        )
    }

    @Test
    fun `renames a variable that would hide a member its holder inherits`() {
        // given: a node whose variables are named like the members of VariableDefinitions
        val variables = listOf("all", "inputs", "outputs", "orderId").map { VariableDefinition(name = it, direction = VariableDirection.INPUT) }
        val model = testProcessModel(flowNodes = listOf(FlowNodeDefinition.Unknown(id = "task", variables = variables)))

        // when: we build the process API file
        val result = underTest.generateCode(csharpApi(model)).single()

        // then: only the colliding variables are renamed, in the declaration and in the list
        assertThat(result.content).contains("public global::Miragon.BpmnToCode.Runtime.VariableName.Input All_ { get; } = new(ProcessVariables.All);")
        assertThat(result.content).contains(
            "public override global::System.Collections.Generic.IReadOnlyList<global::Miragon.BpmnToCode.Runtime.VariableName> All => new global::Miragon.BpmnToCode.Runtime.VariableName[] { All_, Inputs_, OrderId, Outputs_ };",
        )
    }

    @Test
    fun `maps content of id to valid identifier format`() {
        // given: a model with flow nodes whose ids use dashes
        val defaultModel = testBikeLeasingModel()
        val modifiedNodes = defaultModel.flowNodes.map { it.withId(it.id?.replace("_", "-")) }
        val modelApi = csharpApi(testBikeLeasingModel(flowNodes = modifiedNodes))

        // when: we build the process API file
        val result = underTest.generateCode(modelApi).single()

        // then: dashes never leak into an identifier, only into the string values
        val declarations = result.content.lines().filter {
            it.contains("public const string") || it.contains("public static class") || it.contains("public sealed class")
        }
        assertThat(declarations).isNotEmpty()
        assertThat(declarations.map { it.substringBefore("=") }).noneMatch { it.contains("-") }
    }

    private fun csharpApi(model: ProcessModel) = testProcessModelApi(
        model = model,
        packagePath = "de.emaarco.example",
        language = OutputLanguage.CSHARP,
    )
}
