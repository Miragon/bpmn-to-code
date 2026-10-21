using System.Diagnostics;

var cliDirectory = Path.Combine(AppContext.BaseDirectory, "cli");
var java = Path.Combine(cliDirectory, "runtime", "bin", OperatingSystem.IsWindows() ? "java.exe" : "java");

if (!File.Exists(java))
{
    Console.Error.WriteLine($"bpmn-to-code: this package brings no Java runtime for this platform, {java} is missing");
    return 1;
}

if (!OperatingSystem.IsWindows())
{
    MarkExecutable(java);
    MarkExecutable(Path.Combine(cliDirectory, "runtime", "lib", "jspawnhelper"));
}

var startInfo = new ProcessStartInfo(java) { UseShellExecute = false };
startInfo.ArgumentList.Add("-cp");
startInfo.ArgumentList.Add(Path.Combine(cliDirectory, "lib", "*"));
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
catch (System.ComponentModel.Win32Exception exception)
{
    Console.Error.WriteLine($"bpmn-to-code: could not start {java}: {exception.Message}");
    return 1;
}

[System.Runtime.Versioning.UnsupportedOSPlatform("windows")]
static void MarkExecutable(string path)
{
    const UnixFileMode executable = UnixFileMode.UserExecute | UnixFileMode.GroupExecute | UnixFileMode.OtherExecute;
    var mode = File.GetUnixFileMode(path);
    if ((mode & executable) == executable)
    {
        return;
    }
    try
    {
        File.SetUnixFileMode(path, mode | executable);
    }
    catch (Exception exception) when (exception is UnauthorizedAccessException or IOException)
    {
        Console.Error.WriteLine($"bpmn-to-code: could not mark {path} as executable: {exception.Message}");
    }
}
