using System.Collections.Generic;
using System.Linq;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Xunit;

namespace BpmnToCode.Runtime.Tests;

public class GeneratedApiCompilationTest
{
    private const string ImpossiblePath = """
        using Miragon.BpmnToCode.Runtime;
        using Api = MiraVelo.Process.BikeLeasingProcessApi;

        namespace MiraVelo.Process.Consumer;

        public static class Walk
        {
            public static string[] Ids() => ProcessPath.From(Api.FlowNodes.GatewayIsSolvent).Then(n => n.GatewayFork).Ids;
        }
        """;

    [Theory]
    [InlineData("zeebe/bike-leasing.bpmn", "ZEEBE")]
    [InlineData("c7/bike-leasing.bpmn", "CAMUNDA_7")]
    [InlineData("operaton/bike-leasing.bpmn", "OPERATON")]
    [InlineData("zeebe/membership.bpmn", "ZEEBE")]
    [InlineData("c7/membership.bpmn", "CAMUNDA_7")]
    public void GeneratedApiOfASharedModelCompiles(string model, string engine)
    {
        // when
        var generated = GeneratedApi.Generate(engine, GeneratedApi.SharedModel(model));

        // then
        AssertCompiles(generated.Values);
    }

    [Fact]
    public void GeneratedApiOfNestedSubProcessesCompiles()
    {
        // when
        var generated = GeneratedApi.Generate("ZEEBE", GeneratedApi.CoreModel("nested-subprocess.bpmn"));

        // then
        AssertCompiles(generated.Values);
    }

    [Fact]
    public void GeneratedApisOfTwoProcessesSharingJobTypesAndMessagesCompile()
    {
        // given
        var bikeLeasing = GeneratedApi.SharedModel("zeebe/bike-leasing.bpmn");
        var copy = bikeLeasing.Replace("id=\"bikeLeasing\"", "id=\"bikeLeasingCopy\"");

        // when
        var generated = GeneratedApi.Generate("ZEEBE", bikeLeasing, copy);

        // then
        AssertCompiles(generated.Values);
    }

    [Fact]
    public void GeneratedApiWithSeveralSequenceFlowsToTheSameElementCompiles()
    {
        // when
        var generated = GeneratedApi.Generate("ZEEBE", GeneratedApi.Fixture("approval.bpmn"));

        // then
        Assert.Contains(
            "public global::Miragon.BpmnToCode.Runtime.SequenceFlows<TaskApproveNode> TaskApprove => _taskApprove ??= new(FlowNodes.TaskApprove, new global::Miragon.BpmnToCode.Runtime.SequenceFlow<TaskApproveNode>[] {",
            generated["ApprovalProcessApi.cs"]);
        AssertCompiles(generated.Values);
    }

    [Fact]
    public void GeneratedApiWithElementsNamedApartOnlyByANodeSuffixCompiles()
    {
        // when
        var generated = GeneratedApi.Generate("ZEEBE", GeneratedApi.Fixture("suffixed.bpmn"));

        // then
        Assert.Contains("public static TaskNode_ Task => TaskNode_.Instance;", generated["SuffixedProcessApi.cs"]);
        AssertCompiles(generated.Values);
    }

    [Fact]
    public void GeneratedApiWithVariablesNamedLikeTheMembersOfTheirHolderCompiles()
    {
        // given
        var renamed = GeneratedApi.SharedModel("zeebe/bike-leasing.bpmn")
            .Replace("target=\"bikeIds\"", "target=\"inputs\"")
            .Replace("target=\"age\"", "target=\"outputs\"");

        // when
        var generated = GeneratedApi.Generate("ZEEBE", renamed);

        // then
        Assert.Contains(" Inputs_ { get; }", generated["BikeLeasingProcessApi.cs"]);
        Assert.Contains(" Outputs_ { get; }", generated["BikeLeasingProcessApi.cs"]);
        AssertCompiles(generated.Values);
    }

    [Fact]
    public void GeneratedApiWithAnElementNamedSystemAndAVariableNamedLikeASharedTypeCompiles()
    {
        // given
        var renamed = GeneratedApi.SharedModel("zeebe/bike-leasing.bpmn")
            .Replace("gateway_fork", "system")
            .Replace("target=\"bikeIds\"", "target=\"processVariables\"");

        // when
        var generated = GeneratedApi.Generate("ZEEBE", renamed);

        // then
        AssertCompiles(generated.Values);
    }

    [Fact]
    public void GeneratedApiCompilesWithNullableReferenceTypesDisabled()
    {
        // given
        var generated = GeneratedApi.Generate("ZEEBE", GeneratedApi.SharedModel("zeebe/bike-leasing.bpmn"));

        // when
        var problems = CSharpCompiler.Problems(generated.Values, nullable: NullableContextOptions.Disable);

        // then
        Assert.Empty(Describe(problems));
    }

    [Fact]
    public void GeneratedApiCompilesWhenTheProjectWritesADocumentationFile()
    {
        // given
        var generated = GeneratedApi.Generate("ZEEBE", GeneratedApi.SharedModel("zeebe/bike-leasing.bpmn"));

        // when
        var problems = CSharpCompiler.Problems(generated.Values, documentation: DocumentationMode.Diagnose);

        // then
        Assert.Empty(Describe(problems));
    }

    [Fact]
    public void AProcessPathTheModelDoesNotContainDoesNotCompile()
    {
        // given: a walk from the solvency gateway straight to the fork, which no sequence flow connects
        var generated = GeneratedApi.Generate("ZEEBE", GeneratedApi.SharedModel("zeebe/bike-leasing.bpmn"));

        // when
        var problems = CSharpCompiler.Problems(generated.Values.Append(ImpossiblePath));

        // then: the compiler rejects the missing successor
        var missingSuccessor = Assert.Single(problems);
        Assert.Equal("CS1061", missingSuccessor.Id);
        Assert.Contains("GatewayFork", missingSuccessor.GetMessage());
    }

    private static void AssertCompiles(IEnumerable<string> sources) => Assert.Empty(Describe(CSharpCompiler.Problems(sources)));

    private static IEnumerable<string> Describe(IEnumerable<Diagnostic> problems) => problems.Select(problem => problem.ToString());
}
