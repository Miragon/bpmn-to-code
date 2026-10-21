using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Miragon.BpmnToCode.Runtime;

namespace BpmnToCode.Runtime.Tests;

/// <summary>
/// Compiles sources in memory against .NET 8 and the runtime package, with the settings of a consuming project:
/// C# 12, no implicit usings, and every warning counted as an error.
/// </summary>
internal static class CSharpCompiler
{
    private static readonly IReadOnlyList<MetadataReference> References = FrameworkReferences()
        .Append(MetadataReference.CreateFromFile(typeof(ProcessPath).Assembly.Location))
        .ToList();

    public static IReadOnlyList<Diagnostic> Problems(
        IEnumerable<string> sources,
        NullableContextOptions nullable = NullableContextOptions.Enable,
        DocumentationMode documentation = DocumentationMode.Parse)
    {
        var parseOptions = new CSharpParseOptions(LanguageVersion.CSharp12, documentation);
        var syntaxTrees = sources.Select(source => CSharpSyntaxTree.ParseText(source, parseOptions));
        var options = new CSharpCompilationOptions(OutputKind.DynamicallyLinkedLibrary, nullableContextOptions: nullable, warningLevel: 8);
        var compilation = CSharpCompilation.Create("Generated", syntaxTrees, References, options);

        using var assembly = new MemoryStream();
        var result = compilation.Emit(assembly);
        return result.Diagnostics.Where(diagnostic => diagnostic.Severity >= DiagnosticSeverity.Warning).ToList();
    }

    private static IEnumerable<MetadataReference> FrameworkReferences()
    {
        var frameworkDirectory = Path.GetDirectoryName(typeof(object).Assembly.Location)!;
        var trustedAssemblies = ((string)AppContext.GetData("TRUSTED_PLATFORM_ASSEMBLIES")!).Split(Path.PathSeparator);
        return trustedAssemblies
            .Where(path => Path.GetDirectoryName(path) == frameworkDirectory)
            .Select(path => MetadataReference.CreateFromFile(path));
    }
}
