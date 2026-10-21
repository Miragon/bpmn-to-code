# Miragon.BpmnToCode.MSBuild

> **Experimental, not yet published.** This package may change in a minor release and is not on nuget.org yet.

Generates the type-safe C# Process API of every BPMN model in a project during `dotnet build`, with [bpmn-to-code](https://miragon.github.io/bpmn-to-code/), and adds `Miragon.BpmnToCode.Runtime`, the package the generated code is built from.

```xml
<ItemGroup>
  <PackageReference Include="Miragon.BpmnToCode.MSBuild" Version="x.y.z" />
</ItemGroup>
<PropertyGroup>
  <BpmnToCodeProcessEngine>ZEEBE</BpmnToCodeProcessEngine>
  <BpmnToCodeFilePattern>bpmn/*.bpmn</BpmnToCodeFilePattern>
</PropertyGroup>
```

The generation runs the .NET tool `Miragon.BpmnToCode` of the same version through `dotnet tool exec`, which needs the .NET 10 SDK. Every setting: https://miragon.github.io/bpmn-to-code/getting-started/dotnet
