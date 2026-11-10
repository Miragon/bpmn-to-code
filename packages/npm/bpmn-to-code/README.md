# @miragon/bpmn-to-code

> **Experimental, not yet published.** This package may change in a minor release and is not on npm yet.

**Generate a typed TypeScript API from your BPMN models in an npm script.**

The command-line tool of [bpmn-to-code](https://github.com/Miragon/bpmn-to-code) as a development dependency: it turns element ids, job types and message names into constants for your workers and frontends, for Camunda 8 / Zeebe, Camunda 7 and Operaton. It needs Node 18 or newer, an ES module project (CommonJS needs Node 20.19 or newer) and Java 21 or newer from `JAVA_HOME` or the `PATH`.

## Install

```bash
npm install --save-exact @miragon/bpmn-to-code-runtime
npm install --save-dev --save-exact @miragon/bpmn-to-code
```

The generated files import their types from [`@miragon/bpmn-to-code-runtime`](https://github.com/Miragon/bpmn-to-code/tree/main/packages/npm/bpmn-to-code-runtime), a regular dependency. This package declares it as a peer dependency of exactly its own version, so npm refuses a runtime of another version.

## Get started

```bash
npx bpmn-to-code generate-api --baseDir . --filePattern "bpmn/*.bpmn" --processEngine ZEEBE \
  --outputLanguage TYPESCRIPT --outputFolderPath src --packagePath process
```

The files land in `src/process`. Every flag is listed in the [CLI reference](https://github.com/Miragon/bpmn-to-code/tree/main/bpmn-to-code-cli#commands-and-flags).

## Help and feedback

- **Documentation:** [TypeScript guide](https://miragon.github.io/bpmn-to-code/getting-started/typescript)
- **Bugs and ideas:** [open an issue](https://github.com/Miragon/bpmn-to-code/issues)

## License

[MIT](https://github.com/Miragon/bpmn-to-code/blob/main/LICENSE)
