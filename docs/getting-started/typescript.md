# TypeScript Setup

Two npm packages of the same version work together:

| Package | What it is |
|---|---|
| `@miragon/bpmn-to-code` | The [command-line tool](https://github.com/Miragon/bpmn-to-code/tree/main/bpmn-to-code-cli) with its own Java runtime, a development dependency |
| `@miragon/bpmn-to-code-runtime` | The types the generated code is built from, and [`ProcessPath`](/guide/process-path#from-typescript), a regular dependency |

Neither a JDK nor Gradle or Maven is needed. The generated TypeScript holds the constants, the information about each element and what follows it, for workers and for frontends.

**Requirements:** Node 18 or newer, on Linux (x64, ARM64), macOS (Intel, Apple Silicon) or Windows (x64).

::: warning Experimental
TypeScript output is experimental and may change in a minor release. See [TypeScript specifics](/guide/generated-api#typescript-specifics).
:::

## 1. Install the packages

<!-- x-release-please-start-version -->
```bash
npm install --save-exact @miragon/bpmn-to-code-runtime@6.5.0
npm install --save-dev --save-exact @miragon/bpmn-to-code@6.5.0
```
<!-- x-release-please-end -->

The tool declares the runtime as a peer dependency of exactly its own version, so npm refuses a runtime of another version. npm installs the tool's package for the current platform along with it. An install with `--omit=optional` skips that package, and the command fails naming it.

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
