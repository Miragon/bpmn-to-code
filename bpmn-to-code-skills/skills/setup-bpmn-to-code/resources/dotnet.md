# .NET

The package `Miragon.BpmnToCode.MSBuild` generates the C# Process APIs during `dotnet build` and adds the runtime package `Miragon.BpmnToCode.Runtime`. It needs the .NET 10 SDK; the generated code targets .NET 8 and later. No JDK: the build fetches the tool `Miragon.BpmnToCode.Cli` with its own Java runtime. C# output is experimental.

## Package and settings

```xml
<!-- MiraVelo.Process.csproj -->
<ItemGroup>
  <PackageReference Include="Miragon.BpmnToCode.MSBuild" Version="<version>" />
</ItemGroup>

<PropertyGroup>
  <BpmnToCodeProcessEngine>ZEEBE</BpmnToCodeProcessEngine>
  <BpmnToCodeFilePattern>bpmn/*.bpmn</BpmnToCodeFilePattern>
</PropertyGroup>
```

- `BpmnToCodeProcessEngine` is required. The file pattern defaults to `**/*.bpmn` below the project directory.
- The namespace defaults to `$(RootNamespace)`; set `BpmnToCodePackagePath` for another one.
- The files go to `obj/` and are compiled with the project. To commit them, set `BpmnToCodeOutputFolderPath` to a folder inside the project.

## Generate

```bash
dotnet build
```

A build only generates again when a model or the project file changed.

## Validate

```bash
dotnet build -t:BpmnToCodeValidate
```

Optional properties: `BpmnToCodeFailOnWarning`, `BpmnToCodeDisabledRules` (comma-separated). Run it as a CI step to replace the model validation test of the JVM setups.
