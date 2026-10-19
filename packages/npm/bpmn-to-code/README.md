# @miragon/bpmn-to-code

Generates a type-safe API from BPMN process models, in TypeScript, Kotlin, Java or C#, and validates the models. No Java installation needed: the tool brings its own runtime for your platform.

```bash
npm install --save-dev @miragon/bpmn-to-code
```

```json
{
  "scripts": {
    "generate": "bpmn-to-code generate-api --baseDir bpmn --filePattern \"*.bpmn\" --processEngine ZEEBE --outputLanguage TYPESCRIPT --outputFolderPath src/process"
  }
}
```

```ts
import { BikeLeasingProcessApi } from "./process/BikeLeasingProcessApi.js";

const step = BikeLeasingProcessApi.FlowNodes.ServiceTaskValidateApplication;
console.log(step.name, step.jobType);
```

Commands: `generate-api` and `validate`. Run `bpmn-to-code <command> --help` for the flags, or see the [documentation](https://miragon.github.io/bpmn-to-code/getting-started/cli).

Supported platforms: macOS (arm64, x64), Linux (arm64, x64, glibc) and Windows (x64).
