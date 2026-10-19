# Command-line tool

For projects without Gradle, Maven or MSBuild. The tool is experimental. It calls the same generator as the plugins, so the output is identical.

## Install

Use an existing `bpmn-to-code` on the PATH (`bpmn-to-code --version`). Otherwise ask the user to install it; never install it yourself:

- macOS / Linux: `brew install miragon/tap/bpmn-to-code`
- any platform: the zip for the platform from the GitHub release `https://github.com/Miragon/bpmn-to-code/releases`. It brings its own Java runtime, and the command is `bin/bpmn-to-code` (Windows: `bin\bpmn-to-code.cmd`).

## Generate

Every flag is required. Relative paths resolve against the directory the tool starts in.

```bash
bpmn-to-code generate-api \
  --baseDir . \
  --filePattern "bpmn/*.bpmn" \
  --processEngine ZEEBE \
  --outputLanguage CSHARP \
  --outputFolderPath src/MiraVelo.Process \
  --packagePath MiraVelo.Process
```

- For C#, `--packagePath` is the namespace. The generated code needs the NuGet package `Miragon.BpmnToCode.Runtime` in the same version.
- Exit codes: `0` success, `1` models fail or cannot be read, `2` wrong call.

## Hook into the build

There is no build plugin, so make generation reproducible:

- **.NET:** use the MSBuild package instead, see [dotnet.md](dotnet.md).
- **Elsewhere:** a script or task-runner entry, e.g. `scripts/generate-process-api.sh`.

Show the user both options and let them choose.

## Validate

```bash
bpmn-to-code validate --baseDir . --filePattern "bpmn/*.bpmn" --processEngine ZEEBE
```

Optional flags: `--failOnWarning`, `--disabledRules rule-a,rule-b`. Run it as a CI step to replace the model validation test of the JVM setups.
