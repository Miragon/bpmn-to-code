using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Reflection;

namespace BpmnToCode.Runtime.Tests;

internal static class GeneratedApi
{
    public const string Namespace = "MiraVelo.Process";

    private static readonly TimeSpan Timeout = TimeSpan.FromMinutes(2);

    public static readonly string RepositoryRoot = Metadata("RepositoryRoot");

    public static string SharedModel(string path) => File.ReadAllText(Path.Combine(RepositoryRoot, "shared", "bpmn", path));

    public static string CoreModel(string fileName) =>
        File.ReadAllText(Path.Combine(RepositoryRoot, "bpmn-to-code-core", "src", "test", "resources", "bpmn", fileName));

    public static string Fixture(string fileName) => File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "Fixtures", fileName));

    public static IReadOnlyDictionary<string, string> Generate(string engine, params string[] bpmnXmls)
    {
        var workDirectory = Directory.CreateTempSubdirectory("bpmn-to-code-csharp").FullName;
        var modelDirectory = Directory.CreateDirectory(Path.Combine(workDirectory, "models")).FullName;
        var outputDirectory = Path.Combine(workDirectory, "src");
        for (var index = 0; index < bpmnXmls.Length; index++)
        {
            File.WriteAllText(Path.Combine(modelDirectory, $"process-{index}.bpmn"), bpmnXmls[index]);
        }

        RunCli(
            "generate-api", "--baseDir", modelDirectory, "--filePattern", "*.bpmn", "--processEngine", engine,
            "--outputLanguage", "CSHARP", "--outputFolderPath", outputDirectory, "--packagePath", Namespace);

        var generated = Directory.EnumerateFiles(outputDirectory, "*.cs", SearchOption.AllDirectories)
            .ToDictionary(path => Path.GetFileName(path), File.ReadAllText);
        if (generated.Count == 0)
        {
            throw new InvalidOperationException($"The CLI generated no C# file into {outputDirectory}");
        }

        return generated;
    }

    private static void RunCli(params string[] arguments)
    {
        var cli = Metadata("BpmnToCodeCli");
        if (!File.Exists(cli))
        {
            throw new InvalidOperationException($"No CLI at {cli}. Run ./gradlew :bpmn-to-code-cli:installDist or set BPMN_TO_CODE_CLI.");
        }

        var startInfo = new ProcessStartInfo(cli) { RedirectStandardOutput = true, RedirectStandardError = true };
        arguments.ToList().ForEach(startInfo.ArgumentList.Add);
        using var process = Process.Start(startInfo)!;
        var output = process.StandardOutput.ReadToEndAsync();
        var error = process.StandardError.ReadToEndAsync();
        if (!process.WaitForExit(Timeout))
        {
            process.Kill();
            throw new TimeoutException($"The CLI did not finish within {Timeout}");
        }

        if (process.ExitCode != 0)
        {
            throw new InvalidOperationException($"The CLI failed with exit code {process.ExitCode}:\n{output.Result}{error.Result}");
        }
    }

    private static string Metadata(string key) => typeof(GeneratedApi).Assembly
        .GetCustomAttributes<AssemblyMetadataAttribute>()
        .Single(attribute => attribute.Key == key).Value!;
}
