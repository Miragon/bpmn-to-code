# bpmn-to-code

Generates type-safe APIs from BPMN process models and validates the models, for Zeebe (Camunda 8), Camunda 7 and Operaton. The tool needs the .NET 10 SDK and Java 21 or newer from `JAVA_HOME` or the `PATH`.

```bash
dotnet tool install Miragon.BpmnToCode
dotnet bpmn-to-code generate-api --baseDir . --filePattern "bpmn/*.bpmn" --processEngine ZEEBE \
  --outputLanguage CSHARP --outputFolderPath src/Process --packagePath MiraVelo.Process
```

Documentation: https://miragon.github.io/bpmn-to-code/getting-started/dotnet
