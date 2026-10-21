# Command-line tool

For projects without Gradle, Maven or a `*.csproj` (for that, see [dotnet.md](dotnet.md)), for example Node with TypeScript output. The tool is experimental. It calls the same generator as the plugins, so the output is identical.

## Install

Use an existing `bpmn-to-code` on the PATH (`bpmn-to-code --version`). Otherwise ask the user to install it; never install it yourself:

- macOS / Linux: `brew install miragon/tap/bpmn-to-code`
- any platform: the zip for the platform from the GitHub release `https://github.com/Miragon/bpmn-to-code/releases`. It brings its own Java runtime, and the command is `bin/bpmn-to-code` (Windows: `bin\bpmn-to-code.cmd`).
- Node project: propose the npm packages, `npm install --save-exact @miragon/bpmn-to-code-runtime@<release>` and `npm install --save-dev --save-exact @miragon/bpmn-to-code@<release>`. The tool needs Java 21 or newer (`JAVA_HOME` or the `PATH`), and the command is `bpmn-to-code` inside npm scripts or `npx bpmn-to-code`.

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

- For C#, `--packagePath` becomes the namespace, each segment in PascalCase (`miravelo.process` becomes `Miravelo.Process`). The generated C# needs `Miragon.BpmnToCode.Runtime` of the same version: `dotnet add package Miragon.BpmnToCode.Runtime --version <release>`.
- For TypeScript, `--packagePath` names the directory below `--outputFolderPath`, with dots as separators (`process` → `src/process`). The generated TypeScript imports `@miragon/bpmn-to-code-runtime`, which must have the version of the tool; npm enforces it through a peer dependency.
- Exit codes: `0` success, `1` models fail or cannot be read, `2` wrong call.

## Hook into the build

There is no build plugin, so make generation reproducible:

- **Node:** an npm script, `"generate:process": "bpmn-to-code generate-api …"`, called from `prebuild`.
- **Elsewhere:** a script or task-runner entry, e.g. `scripts/generate-process-api.sh`.

Show the user both options and let them choose.

## Validate

```bash
bpmn-to-code validate --baseDir . --filePattern "bpmn/*.bpmn" --processEngine ZEEBE
```

Optional flags: `--failOnWarning`, `--disabledRules rule-a,rule-b`. Run it as a CI step to replace the model validation test of the JVM setups.
