using System.Diagnostics;

var cliDirectory = Path.Combine(AppContext.BaseDirectory, "cli");
var java = Path.Combine(cliDirectory, "runtime", "bin", OperatingSystem.IsWindows() ? "java.exe" : "java");

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

using var cli = Process.Start(startInfo)!;
cli.WaitForExit();
return cli.ExitCode;

[System.Runtime.Versioning.UnsupportedOSPlatform("windows")]
static void MarkExecutable(string path)
{
    const UnixFileMode executable = UnixFileMode.UserExecute | UnixFileMode.GroupExecute | UnixFileMode.OtherExecute;
    var mode = File.GetUnixFileMode(path);
    if ((mode & executable) != executable)
    {
        File.SetUnixFileMode(path, mode | executable);
    }
}
