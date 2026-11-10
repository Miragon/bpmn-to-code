# Miragon.BpmnToCode.MSBuild

> **Experimental, not yet published.** This package may change in a minor release and is not on nuget.org yet.

**Generate the typed C# Process API of every BPMN model in your project during `dotnet build`.**

The MSBuild integration of [bpmn-to-code](https://github.com/Miragon/bpmn-to-code): it turns element ids, job types and message names into constants for your workers and tests, and adds [`Miragon.BpmnToCode.Runtime`](https://github.com/Miragon/bpmn-to-code/tree/main/packages/nuget/BpmnToCode.Runtime), the package the generated code is built from. It needs the .NET 10 SDK and Java 21 or newer from `JAVA_HOME` or the `PATH`.

## Install

```xml
<ItemGroup>
  <PackageReference Include="Miragon.BpmnToCode.MSBuild" Version="x.y.z" />
</ItemGroup>
```

## Get started

```xml
<PropertyGroup>
  <BpmnToCodeProcessEngine>ZEEBE</BpmnToCodeProcessEngine>
  <BpmnToCodeFilePattern>bpmn/*.bpmn</BpmnToCodeFilePattern>
</PropertyGroup>
```

`dotnet build` now generates one Process API per BPMN file into `obj/` and compiles it with the project. On the first build it fetches the matching .NET tool `Miragon.BpmnToCode` from your NuGet feeds; besides Java 21, nothing has to be installed.

## Help and feedback

- **Documentation:** every setting is in the [.NET guide](https://miragon.github.io/bpmn-to-code/getting-started/dotnet)
- **Bugs and ideas:** [open an issue](https://github.com/Miragon/bpmn-to-code/issues)

## License

[MIT](https://github.com/Miragon/bpmn-to-code/blob/main/LICENSE)
