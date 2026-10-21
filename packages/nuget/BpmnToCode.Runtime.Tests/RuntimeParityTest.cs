using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using Miragon.BpmnToCode.Runtime;
using Xunit;

namespace BpmnToCode.Runtime.Tests;

public class RuntimeParityTest
{
    [Theory]
    [InlineData(typeof(BpmnElementType), "BpmnElementType.kt")]
    [InlineData(typeof(BpmnEventType), "BpmnEventType.kt")]
    [InlineData(typeof(TimerType), "TimerType.kt")]
    [InlineData(typeof(BpmnEngine), "BpmnEngine.kt")]
    public void EnumMirrorsTheJvmRuntimeEnumInPascalCase(Type csharpEnum, string kotlinSource)
    {
        // given
        var kotlinConstants = KotlinEnumConstants(kotlinSource);

        // when
        var csharpConstants = Enum.GetNames(csharpEnum);

        // then
        Assert.Equal(kotlinConstants.Select(PascalCase).Order(), csharpConstants.Order());
    }

    [Fact]
    public void VariableNamesOfEveryDirectionPrintTheirRawName()
    {
        // given
        VariableName[] variableNames = { new VariableName.Input("bikeIds"), new VariableName.Output("bikeIds"), new VariableName.InOut("bikeIds") };

        // then
        Assert.All(variableNames, variableName => Assert.Equal("bikeIds", variableName.ToString()));
    }

    private static IEnumerable<string> KotlinEnumConstants(string fileName)
    {
        var source = File.ReadAllText(Path.Combine(GeneratedApi.RepositoryRoot, "bpmn-to-code-runtime", "src", "main", "kotlin", "io", "miragon", "bpmn", "runtime", fileName));
        var body = source[(source.IndexOf("enum class", StringComparison.Ordinal))..];
        return Regex.Matches(body, @"^\s+([A-Z][A-Z0-9_]*),", RegexOptions.Multiline).Select(match => match.Groups[1].Value).ToList();
    }

    private static string PascalCase(string constant) => string.Concat(
        constant.Split('_', StringSplitOptions.RemoveEmptyEntries).Select(word => char.ToUpperInvariant(word[0]) + word[1..].ToLowerInvariant()));
}
