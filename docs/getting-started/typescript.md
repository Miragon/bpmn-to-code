# TypeScript Setup

Two npm packages of the same version work together:

| Package | What it is |
|---|---|
| `@miragon/bpmn-to-code` | The [command-line tool](https://github.com/Miragon/bpmn-to-code/tree/main/bpmn-to-code-cli) a development dependency that runs on Java 21 |
| `@miragon/bpmn-to-code-runtime` | The types the generated code is built from, and [`ProcessPath`](/guide/process-path#from-typescript), a regular dependency |

Neither Gradle nor Maven is needed. The generated TypeScript holds the constants, the information about each element and what follows it, for workers and for frontends.

**Requirements:** Node 18 or newer, in an ES module project (`"type": "module"`, or a bundler); the runtime package ships as ES module only, so a CommonJS project needs Node 20.19 or newer, and Jest needs its ES module support. The tool needs Java 21 or newer, from `JAVA_HOME` or the `PATH`; it fails with a message naming the version it found otherwise.

::: warning Experimental
TypeScript output is experimental and may change in a minor release. See [TypeScript specifics](/guide/generated-api#typescript-specifics).
:::

## 1. Install the packages

<!-- x-release-please-start-version -->
```bash
npm install --save-exact @miragon/bpmn-to-code-runtime@6.6.0
npm install --save-dev --save-exact @miragon/bpmn-to-code@6.6.0
```
<!-- x-release-please-end -->

The tool declares the runtime as a peer dependency of exactly its own version, so npm refuses a runtime of another version.

## 2. Generate the API

Add a script to `package.json`:

```json
{
  "scripts": {
    "generate:process": "bpmn-to-code generate-api --baseDir . --filePattern \"bpmn/*.bpmn\" --processEngine ZEEBE --outputLanguage TYPESCRIPT --outputFolderPath src --packagePath process"
  }
}
```

```bash
npm run generate:process
```

The files land in `src/process`. Every flag is described on the [README of the command-line tool](https://github.com/Miragon/bpmn-to-code/tree/main/bpmn-to-code-cli) and in [Configuration](/guide/configuration). To generate before every build, call the script from `prebuild`.

## 3. Use it

```ts
import { BikeLeasingProcessApi } from "./process/BikeLeasingProcessApi.js";
import { ProcessPath } from "@miragon/bpmn-to-code-runtime";
import { ServiceTasks } from "./process/ServiceTasks.js";

const nodes = BikeLeasingProcessApi.FlowNodes;

// a worker
const jobType: typeof ServiceTasks.MIRAVELO_SEND_CONTRACT = nodes.ServiceTaskSendContract.jobType;

// a frontend
const step = nodes.ServiceTaskSendContract;
showLabel(step.name ?? step.id);

// what follows a step, checked at compile time
const solvent = nodes.BusinessRuleTaskCheckCreditRating.next.gatewayIsSolvent.target;
solvent.next.subProcessConcludeContract.flow.name; // "Yes"

// a process test: the route an instance took, checked against the model
const ids = ProcessPath.from(nodes.StartEventLeasingRequestReceived)
  .then((next) => next.serviceTaskValidateApplication)
  .then((next) => next.boundaryApplicationInvalid)
  .then((next) => next.gatewayCollectRejections).ids;
```

More on paths: [Process paths from TypeScript](/guide/process-path#from-typescript).
