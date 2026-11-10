# @miragon/bpmn-to-code-runtime

> **Experimental, not yet published.** This package may change in a minor release and is not on npm yet.

**The types your generated TypeScript Process API is built from, and compile-checked process paths.**

The runtime of [bpmn-to-code](https://github.com/Miragon/bpmn-to-code) for TypeScript: `FlowNode`, `SequenceFlows`, `BpmnErrorDefinition` and the other types the generated code imports, plus `ProcessPath`, a walk over the generated `FlowNodes` that stops compiling when the model changes. It ships as ES module only: a CommonJS project needs Node 20.19 or newer, and Jest needs its ES module support.

## Install

```bash
npm install --save-exact @miragon/bpmn-to-code-runtime
npm install --save-dev --save-exact @miragon/bpmn-to-code
```

Use the same version for both: the generator writes code against the runtime of its own version, and [`@miragon/bpmn-to-code`](https://github.com/Miragon/bpmn-to-code/tree/main/packages/npm/bpmn-to-code) declares this package as a peer dependency of exactly that version.

## Get started

```ts
import { ProcessPath } from "@miragon/bpmn-to-code-runtime";
import { BikeLeasingProcessApi } from "./process/BikeLeasingProcessApi.js";

const ids = ProcessPath.from(BikeLeasingProcessApi.FlowNodes.StartEventLeasingRequestReceived)
  .then((next) => next.serviceTaskValidateApplication)
  .then((next) => next.businessRuleTaskCheckCreditRating).ids;
```

## Help and feedback

- **Documentation:** [TypeScript guide](https://miragon.github.io/bpmn-to-code/getting-started/typescript) and [ProcessPath from TypeScript](https://miragon.github.io/bpmn-to-code/guide/process-path#from-typescript)
- **Bugs and ideas:** [open an issue](https://github.com/Miragon/bpmn-to-code/issues)

## License

[MIT](https://github.com/Miragon/bpmn-to-code/blob/main/LICENSE)
