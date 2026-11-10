# Miragon.BpmnToCode

> **Experimental, not yet published.** This package may change in a minor release and is not on nuget.org yet.

**Generate the typed Process API from your BPMN models with a .NET tool.**

The command-line tool of [bpmn-to-code](https://github.com/Miragon/bpmn-to-code) as a .NET tool: it generates and validates for Camunda 8 / Zeebe, Camunda 7 and Operaton. It needs the .NET 10 SDK and Java 21 or newer from `JAVA_HOME` or the `PATH`. To generate during `dotnet build` instead, use [`Miragon.BpmnToCode.MSBuild`](https://github.com/Miragon/bpmn-to-code/tree/main/packages/nuget/BpmnToCode.MSBuild).

## Install

Install it as a local tool, so the version is pinned in the repository, and add the runtime package the generated code is built from:

```bash
dotnet new tool-manifest
dotnet tool install Miragon.BpmnToCode
dotnet add package Miragon.BpmnToCode.Runtime
```

`dotnet new tool-manifest` is needed once per repository; everyone else restores the tool with `dotnet tool restore`. Use the same version for the tool and the runtime.

## Get started

```bash
dotnet bpmn-to-code generate-api --baseDir . --filePattern "bpmn/*.bpmn" --processEngine ZEEBE \
  --outputLanguage CSHARP --outputFolderPath src/Process --packagePath MiraVelo.Process
```

Every flag is listed in the [CLI reference](https://github.com/Miragon/bpmn-to-code/tree/main/bpmn-to-code-cli#commands-and-flags).

## Help and feedback

- **Documentation:** [.NET guide](https://miragon.github.io/bpmn-to-code/getting-started/dotnet)
- **Bugs and ideas:** [open an issue](https://github.com/Miragon/bpmn-to-code/issues)

## License

[MIT](https://github.com/Miragon/bpmn-to-code/blob/main/LICENSE)
