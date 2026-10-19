# .NET

A .NET project generates its C# Process APIs during `dotnet build`, the way a Gradle or Maven build does for Kotlin and Java. Three NuGet packages in the same version work together:

| Package | What it is |
|---|---|
| `Miragon.BpmnToCode.MSBuild` | Generates the Process APIs before the project compiles and adds the runtime package |
| `Miragon.BpmnToCode.Runtime` | The types the generated code is built from, and [`ProcessPath`](/guide/process-path#from-c) |
| `Miragon.BpmnToCode.Cli` | The [command-line tool](/getting-started/cli) as a .NET tool, with its own Java runtime |

**Requirements:** the .NET 10 SDK on macOS (arm64, x64), Linux (arm64, x64, glibc) or Windows (x64). The generated code and the runtime package target .NET 8 and later. No JDK.

::: warning Experimental
The C# output, the .NET tool and the MSBuild integration are experimental and may change in a minor release ([compatibility policy](/contributing/adr/011-compatibility-policy)).
:::

## 1. Add the package

<!-- x-release-please-start-version -->
```xml
<ItemGroup>
  <PackageReference Include="Miragon.BpmnToCode.MSBuild" Version="6.5.0" />
</ItemGroup>
```
<!-- x-release-please-end -->

## 2. Point it at your models

```xml
<PropertyGroup>
  <BpmnToCodeProcessEngine>ZEEBE</BpmnToCodeProcessEngine>
  <BpmnToCodeFilePattern>processes/*.bpmn</BpmnToCodeFilePattern>
</PropertyGroup>
```

`dotnet build` now generates one Process API per BPMN file and the shared definitions into `obj/`, and compiles them with the project. A build only generates again when a model or the project file changed. The settings carry the names of the [Gradle and Maven parameters](/guide/configuration):

| Property | Default | Description |
|---|---|---|
| `BpmnToCodeProcessEngine` | required | `ZEEBE`, `CAMUNDA_7` or `OPERATON` |
| `BpmnToCodeBaseDir` | the project directory | Directory the file pattern is resolved against |
| `BpmnToCodeFilePattern` | `**/*.bpmn` | Glob of the BPMN files below it |
| `BpmnToCodePackagePath` | `$(RootNamespace)` | Namespace of the generated code |
| `BpmnToCodeOutputFolderPath` | `obj/<configuration>/<framework>/bpmn-to-code/` | Where the files go; a folder inside the project works too, if you want to commit them |
| `BpmnToCodeEnabled` | `true` | `false` skips the generation |
| `BpmnToCodeFailOnWarning` | `false` | Whether validation fails on warnings |
| `BpmnToCodeDisabledRules` | none | Comma-separated ids of [validation rules](/validate/) to skip |

The generation runs `Miragon.BpmnToCode.Cli` of the package's own version through `dotnet tool exec`, which fetches it from your NuGet feeds on the first build. Nothing has to be installed globally.

## 3. Use the generated API

```csharp
using MiraVelo.Process;
using Miragon.BpmnToCode.Runtime.Path;

// job types are const strings, usable in attribute arguments and switch labels
switch (job.Type)
{
    case ServiceTasks.MiraveloSendContract:
        SendContract(job);
        break;
}

// a process test asserts the route an instance took
var path = ProcessPath.From(BikeLeasingProcessApi.FlowNodes.StartEventLeasingRequestReceived.Instance)
    .Then(next => next.ServiceTaskValidateApplication)
    .Then(next => next.BusinessRuleTaskCheckCreditRating);
```

What the generated C# looks like in detail: [C# specifics](/guide/generated-api#c-specifics).

## Validate in CI

```bash
dotnet build -t:BpmnToCodeValidate
```

checks the models against the [validation rules](/validate/) without generating code (experimental), with `BpmnToCodeFailOnWarning` and `BpmnToCodeDisabledRules` applied.

## The tool on its own

`Miragon.BpmnToCode.Cli` is the [command-line tool](/getting-started/cli) with every command and flag, as a .NET tool. Each platform has its own package with the Java runtime the tool needs; .NET picks the right one.

```bash
dotnet tool install --global Miragon.BpmnToCode.Cli
bpmn-to-code generate-api --baseDir processes --filePattern "*.bpmn" --processEngine ZEEBE \
  --outputLanguage CSHARP --outputFolderPath Generated --packagePath MiraVelo.Process

# or without installing
dnx Miragon.BpmnToCode.Cli -- validate --baseDir processes --filePattern "*.bpmn" --processEngine ZEEBE
```

To run a tool other than the one of the package's version, for example a local build, set `BpmnToCodeToolCommand` to the command that starts it.
