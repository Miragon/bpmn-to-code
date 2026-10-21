using System.ComponentModel;
using System.Diagnostics;
using System.Text.RegularExpressions;

const int requiredJavaVersion = 21;

var java = ResolveJava();
var javaVersion = MajorVersionOf(java);
if (javaVersion is null)
{
    Console.Error.WriteLine($"bpmn-to-code: needs Java {requiredJavaVersion} or newer; set JAVA_HOME or put java on the PATH");
    return 1;
}
if (javaVersion < requiredJavaVersion)
{
    Console.Error.WriteLine($"bpmn-to-code: needs Java {requiredJavaVersion} or newer, {java} is Java {javaVersion}");
    return 1;
}

var startInfo = new ProcessStartInfo(java) { UseShellExecute = false };
startInfo.ArgumentList.Add("-cp");
startInfo.ArgumentList.Add(Path.Combine(AppContext.BaseDirectory, "cli", "lib", "*"));
startInfo.ArgumentList.Add("io.miragon.bpmn.cli.MainKt");
foreach (var argument in args)
{
    startInfo.ArgumentList.Add(argument);
}

try
{
    using var cli = Process.Start(startInfo)!;
    cli.WaitForExit();
    return cli.ExitCode;
}
catch (Win32Exception exception)
{
    Console.Error.WriteLine($"bpmn-to-code: could not start {java}: {exception.Message}");
    return 1;
}

static string ResolveJava()
{
    var executable = OperatingSystem.IsWindows() ? "java.exe" : "java";
    var javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
    if (!string.IsNullOrEmpty(javaHome) && File.Exists(Path.Combine(javaHome, "bin", executable)))
    {
        return Path.Combine(javaHome, "bin", executable);
    }
    return "java";
}

static int? MajorVersionOf(string java)
{
    var probe = new ProcessStartInfo(java, "-version") { UseShellExecute = false, RedirectStandardError = true };
    try
    {
        using var process = Process.Start(probe)!;
        var versionOutput = process.StandardError.ReadToEnd();
        process.WaitForExit();
        var match = Regex.Match(versionOutput, "version \"(\\d+)(?:\\.(\\d+))?");
        if (!match.Success)
        {
            return 0;
        }
        var first = int.Parse(match.Groups[1].Value);
        if (first == 1 && match.Groups[2].Success)
        {
            return int.Parse(match.Groups[2].Value);
        }
        return first;
    }
    catch (Win32Exception)
    {
        return null;
    }
}
