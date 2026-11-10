# Miragon.BpmnToCode.Runtime

> **Experimental, not yet published.** This package may change in a minor release and is not on nuget.org yet.

**The types your generated C# Process API is built from, and compile-checked process paths.**

The runtime of [bpmn-to-code](https://github.com/Miragon/bpmn-to-code) for C#: `IFlowNode`, `SequenceFlows`, `BpmnErrorDefinition` and the other types the generated code uses, plus `ProcessPath`, a walk over the generated `FlowNodes` that stops compiling when the model changes. It targets .NET 8 and later.

## Install

```xml
<ItemGroup>
  <PackageReference Include="Miragon.BpmnToCode.Runtime" Version="x.y.z" />
</ItemGroup>
```

Use the version of the tool that generated the code. [`Miragon.BpmnToCode.MSBuild`](https://github.com/Miragon/bpmn-to-code/tree/main/packages/nuget/BpmnToCode.MSBuild) adds this package for you.

## Get started

```csharp
using Miragon.BpmnToCode.Runtime;
using Api = MiraVelo.Process.BikeLeasingProcessApi;

string[] ids = ProcessPath.From(Api.FlowNodes.StartEventLeasingRequestReceived)
    .Then(n => n.ServiceTaskValidateApplication)
    .Then(n => n.BusinessRuleTaskCheckCreditRating)
    .Ids;
```

## Help and feedback

- **Documentation:** [.NET guide](https://miragon.github.io/bpmn-to-code/getting-started/dotnet) and [ProcessPath from C#](https://miragon.github.io/bpmn-to-code/guide/process-path#from-c-sharp)
- **Bugs and ideas:** [open an issue](https://github.com/Miragon/bpmn-to-code/issues)

## License

[MIT](https://github.com/Miragon/bpmn-to-code/blob/main/LICENSE)
