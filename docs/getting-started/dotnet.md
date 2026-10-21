# .NET Setup

A .NET project generates its C# Process APIs during `dotnet build`, the way a Gradle or Maven build does for Kotlin and Java. Three NuGet packages of the same version work together:

| Package | What it is |
|---|---|
| `Miragon.BpmnToCode.MSBuild` | Generates the Process APIs before the project compiles and adds the runtime package |
| `Miragon.BpmnToCode.Runtime` | The types the generated code is built from, and [`ProcessPath`](/guide/process-path#from-c-sharp) |
| `Miragon.BpmnToCode` | The [command-line tool](https://github.com/Miragon/bpmn-to-code/tree/main/bpmn-to-code-cli) as a .NET tool, which runs on Java 21 |

**Requirements:** .NET SDK 10 or newer and Java 21 or newer, from `JAVA_HOME` or the `PATH`; the tool fails with a message naming the version it found otherwise. The generated code and the runtime package target .NET 8 and later.

::: warning Experimental, not yet published
The C# output, the MSBuild integration and the .NET tool are experimental and may change in a minor release. The three packages are not on nuget.org yet; until they are, the steps below only work with packages packed from this repository.
:::

## 1. Add the package

<!-- x-release-please-start-version -->
```xml
<ItemGroup>
  <PackageReference Include="Miragon.BpmnToCode.MSBuild" Version="6.6.0" />
</ItemGroup>
```
<!-- x-release-please-end -->

## 2. Point it at your models

```xml
<PropertyGroup>
  <BpmnToCodeProcessEngine>ZEEBE</BpmnToCodeProcessEngine>
  <BpmnToCodeFilePattern>bpmn/*.bpmn</BpmnToCodeFilePattern>
</PropertyGroup>
```

`dotnet build` now generates one Process API per BPMN file and the shared definitions into `obj/`, and compiles them with the project. A build generates again only when a model, the set of models, a setting or the tool version changed; a removed model takes its Process API with it. A model that fails validation stops the build. The settings carry the names of the [Gradle and Maven parameters](/guide/configuration):

| Property | Default | Description |
|---|---|---|
| `BpmnToCodeProcessEngine` | required | `ZEEBE`, `CAMUNDA_7` or `OPERATON` |
| `BpmnToCodeBaseDir` | the project directory | Directory the file pattern is resolved against |
| `BpmnToCodeFilePattern` | required | Glob of the BPMN files below it. Name the folder of the models: a pattern starting with `**/` also finds the copies a build puts into `bin/` |
| `BpmnToCodePackagePath` | `$(RootNamespace)` | Namespace of the generated code, each segment in PascalCase |
| `BpmnToCodeOutputFolderPath` | `obj/<configuration>/<framework>/bpmn-to-code/` | Where the files go; a folder inside the project works too, if you want to commit them |
| `BpmnToCodeEnabled` | `true` | `false` skips the generation |
| `BpmnToCodeFailOnWarning` | `false` | Whether `BpmnToCodeValidate` fails on warnings |
| `BpmnToCodeDisabledRules` | none | Comma-separated ids of [validation rules](/validate/) `BpmnToCodeValidate` skips |

The generation runs the .NET tool `Miragon.BpmnToCode` of the package's own version through `dotnet tool exec`, which fetches it from your NuGet feeds on the first build. Besides Java 21, nothing has to be installed. To run another build of the tool, set `BpmnToCodeToolCommand` to the command that starts it.

## 3. Use the generated API

```csharp
using Miragon.BpmnToCode.Runtime;
using MiraVelo.Process;

// job types are const strings, usable in attribute arguments and switch labels
switch (job.Type)
{
    case ServiceTasks.MiraveloSendContract:
        SendContract(job);
        break;
}

// a process test asserts the route an instance took
string[] ids = ProcessPath.From(BikeLeasingProcessApi.FlowNodes.StartEventLeasingRequestReceived)
    .Then(n => n.ServiceTaskValidateApplication)
    .Then(n => n.BusinessRuleTaskCheckCreditRating)
    .Ids;
```

What the generated C# looks like in detail: [C# specifics](/guide/generated-api#c-specifics).

## Validate in CI

```bash
dotnet build -t:BpmnToCodeValidate
```

checks the models against the [validation rules](/validate/) without generating code, with `BpmnToCodeFailOnWarning` and `BpmnToCodeDisabledRules` applied.

## The tool on its own

Without the MSBuild package, install the tool as a local tool, so the version is pinned in the repository, and add `Miragon.BpmnToCode.Runtime` of the same version to the project:

<!-- x-release-please-start-version -->
```bash
dotnet new tool-manifest   # once per repository, creates .config/dotnet-tools.json
dotnet tool install Miragon.BpmnToCode --version 6.6.0
dotnet add package Miragon.BpmnToCode.Runtime --version 6.6.0
dotnet bpmn-to-code generate-api --baseDir . --filePattern "bpmn/*.bpmn" --processEngine ZEEBE \
  --outputLanguage CSHARP --outputFolderPath Generated --packagePath MiraVelo.Process
```
<!-- x-release-please-end -->

Everyone else restores the tool with `dotnet tool restore`. Every flag is described in the [README of the command-line tool](https://github.com/Miragon/bpmn-to-code/tree/main/bpmn-to-code-cli) and in [Configuration](/guide/configuration).
