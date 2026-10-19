# Miragon.BpmnToCode.Cli

The [bpmn-to-code](https://miragon.github.io/bpmn-to-code/) command-line tool as a .NET tool: generates type-safe C#, TypeScript, Kotlin and Java APIs from BPMN process models and validates the models. Each platform package carries the Java runtime the tool needs, so nothing but the .NET SDK has to be installed.

```sh
dotnet tool install --global Miragon.BpmnToCode.Cli
bpmn-to-code generate-api --baseDir processes --filePattern "*.bpmn" --processEngine ZEEBE \
  --outputLanguage CSHARP --outputFolderPath Generated --packagePath MiraVelo.Process

# or without installing
dnx Miragon.BpmnToCode.Cli -- validate --baseDir processes --filePattern "*.bpmn" --processEngine ZEEBE
```

To generate during `dotnet build`, reference `Miragon.BpmnToCode.MSBuild` instead. See the [command-line guide](https://miragon.github.io/bpmn-to-code/getting-started/cli) for every command and flag.
