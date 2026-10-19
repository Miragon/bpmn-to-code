using System;
using System.Diagnostics;
using System.IO;

namespace Miragon.BpmnToCode.Cli;

/// <summary>Starts the bpmn-to-code command-line tool with the Java runtime of this package and passes its exit code on.</summary>
public static class Program
{
    private const int Failure = 1;
    private const string MainClass = "io.miragon.bpmn.cli.MainKt";
    private static readonly string[] JvmOptions = { "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-Xshare:auto" };

    public static int Main(string[] args)
    {
        var toolDirectory = AppContext.BaseDirectory;
        var java = Path.Combine(toolDirectory, "runtime", "bin", OperatingSystem.IsWindows() ? "java.exe" : "java");
        if (!File.Exists(java))
        {
            Console.Error.WriteLine($"bpmn-to-code: this package carries no Java runtime at {java}; reinstall the tool.");
            return Failure;
        }
        if (!OperatingSystem.IsWindows())
        {
            MakeExecutable(Path.Combine(toolDirectory, "runtime"));
        }
        return Run(java, Path.Combine(toolDirectory, "lib", "*"), args);
    }

    private static int Run(string java, string classpath, string[] args)
    {
        var startInfo = new ProcessStartInfo(java) { UseShellExecute = false };
        foreach (var option in JvmOptions) startInfo.ArgumentList.Add(option);
        startInfo.ArgumentList.Add("-cp");
        startInfo.ArgumentList.Add(classpath);
        startInfo.ArgumentList.Add(MainClass);
        foreach (var argument in args) startInfo.ArgumentList.Add(argument);
        try
        {
            using var process = Process.Start(startInfo)!;
            process.WaitForExit();
            return process.ExitCode;
        }
        catch (System.ComponentModel.Win32Exception exception)
        {
            Console.Error.WriteLine($"bpmn-to-code: could not start {java}: {exception.Message}");
            return Failure;
        }
    }

    /// <summary>NuGet packages do not keep file modes, so the runtime's programs are made executable on the first start.</summary>
    [System.Runtime.Versioning.UnsupportedOSPlatform("windows")]
    private static void MakeExecutable(string runtimeDirectory)
    {
        var programs = Directory.GetFiles(Path.Combine(runtimeDirectory, "bin"));
        var helper = Path.Combine(runtimeDirectory, "lib", "jspawnhelper");
        foreach (var program in File.Exists(helper) ? [.. programs, helper] : programs)
        {
            var mode = File.GetUnixFileMode(program);
            var executable = mode | UnixFileMode.UserExecute | UnixFileMode.GroupExecute | UnixFileMode.OtherExecute;
            if (mode != executable) File.SetUnixFileMode(program, executable);
        }
    }
}
