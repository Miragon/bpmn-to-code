# .NET with the MSBuild package

For a project with a `*.csproj` (.NET SDK 10 or newer, Java 21 or newer from `JAVA_HOME` or the `PATH`). `Miragon.BpmnToCode.MSBuild` generates the C# Process APIs during `dotnet build` and adds `Miragon.BpmnToCode.Runtime`, the package the generated code is built from. Nothing else has to be installed. C# output and these packages are experimental and not on nuget.org yet; tell the user before proposing them.

## Add the package

```xml
<ItemGroup>
  <PackageReference Include="Miragon.BpmnToCode.MSBuild" Version="<release>" />
</ItemGroup>
<PropertyGroup>
  <BpmnToCodeProcessEngine>ZEEBE</BpmnToCodeProcessEngine>
  <BpmnToCodeFilePattern>bpmn/*.bpmn</BpmnToCodeFilePattern>
</PropertyGroup>
```

- `BpmnToCodeFilePattern` is resolved against `BpmnToCodeBaseDir`, the project directory by default.
- `BpmnToCodePackagePath` is the namespace and defaults to `$(RootNamespace)`; each segment becomes PascalCase.
- The files go to `obj/` and are never committed. To commit them, set `BpmnToCodeOutputFolderPath` to a folder in the project, e.g. `Generated/`, and add a CI drift check.
- Models of different engines need one project per engine, as the properties hold one engine.

## Validate

```bash
dotnet build -t:BpmnToCodeValidate
```

Applies `BpmnToCodeFailOnWarning` and `BpmnToCodeDisabledRules` (comma-separated rule ids). Run it as a CI step to replace the model validation test of the JVM setups.

## Verify

Run `dotnet build`. The generated files are below `obj/<configuration>/<framework>/bpmn-to-code/`.
