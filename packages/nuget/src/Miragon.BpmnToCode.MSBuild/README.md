# Miragon.BpmnToCode.MSBuild

Generates the type-safe C# Process API of every BPMN model in a project during `dotnet build`, with [bpmn-to-code](https://miragon.github.io/bpmn-to-code/), and adds `Miragon.BpmnToCode.Runtime`, the package the generated code is built from.

```xml
<ItemGroup>
  <PackageReference Include="Miragon.BpmnToCode.MSBuild" Version="x.y.z" />
</ItemGroup>
<PropertyGroup>
  <BpmnToCodeProcessEngine>ZEEBE</BpmnToCodeProcessEngine>
  <BpmnToCodeFilePattern>processes/*.bpmn</BpmnToCodeFilePattern>
</PropertyGroup>
```

The generation runs the `Miragon.BpmnToCode.Cli` tool of the same version through `dotnet tool exec`, which needs the .NET 10 SDK. See the [.NET guide](https://miragon.github.io/bpmn-to-code/getting-started/dotnet) for every setting.
