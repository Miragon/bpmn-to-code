# bpmn-to-code

> **Experimental, not yet published.** This package may change in a minor release and is not on npm yet.

Generates type-safe APIs from BPMN process models and validates the models, for Zeebe (Camunda 8), Camunda 7 and Operaton. The package installs the command-line tool, which needs Java 21 or newer from `JAVA_HOME` or the `PATH`.

```bash
npm install --save-exact @miragon/bpmn-to-code-runtime
npm install --save-dev --save-exact @miragon/bpmn-to-code
npx bpmn-to-code generate-api --baseDir . --filePattern "bpmn/*.bpmn" --processEngine ZEEBE \
  --outputLanguage TYPESCRIPT --outputFolderPath src --packagePath process
```

The generated files import their types from `@miragon/bpmn-to-code-runtime`, a regular dependency. This package is a development dependency and declares the runtime as a peer dependency of exactly its own version, so npm refuses a runtime of another version.

Documentation: https://miragon.github.io/bpmn-to-code/getting-started/typescript
