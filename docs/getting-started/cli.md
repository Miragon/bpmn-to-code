# Command Line and npm

The command-line tool `bpmn-to-code` runs the generator without Gradle or Maven. It is published on npm as [`@miragon/bpmn-to-code`](https://www.npmjs.com/package/@miragon/bpmn-to-code) and brings its own Java runtime, so a TypeScript project needs nothing but Node. A .NET project uses the same tool from NuGet, see [.NET](/getting-started/dotnet).

**Requirements:** Node 18 or newer on macOS (arm64, x64), Linux (arm64, x64, glibc) or Windows (x64). No JDK.

Without npm, the same tool comes as one zip per platform on every [GitHub release](https://github.com/Miragon/bpmn-to-code/releases) and as the Homebrew formula `miragon/tap/bpmn-to-code`; see the [CLI README](https://github.com/Miragon/bpmn-to-code/tree/main/bpmn-to-code-cli).

::: warning Experimental
The command-line tool and the TypeScript output are new. Commands, flags and exit codes are experimental like the TypeScript output and may change in a minor release ([compatibility policy](/contributing/adr/011-compatibility-policy)).
:::

## 1. Install

```bash
npm install --save-dev --save-exact @miragon/bpmn-to-code
npm install --save-exact @miragon/bpmn-to-code-runtime
```

npm installs `@miragon/bpmn-to-code` and, through its optional dependencies, the one package with the tool and the Java runtime for your platform, such as `@miragon/bpmn-to-code-linux-x64`. Installing with `--omit=optional` leaves that package out, and the tool cannot start.

The generated TypeScript imports its types from `@miragon/bpmn-to-code-runtime`, which also brings [`ProcessPath`](/guide/process-path#from-typescript). Keep both packages on the same version: the generator writes code against the runtime of its own version.

## 2. Generate

Add a script to `package.json` and run it before you build:

```json
{
  "scripts": {
    "generate": "bpmn-to-code generate-api --baseDir bpmn --filePattern \"*.bpmn\" --processEngine ZEEBE --outputLanguage TYPESCRIPT --outputFolderPath src/process"
  }
}
```

```bash
npm run generate
```

This writes one module per process, such as `src/process/BikeLeasingProcessApi.ts`, and the shared definitions (`ServiceTasks.ts`, `Messages.ts`, …). Commit the generated files or generate them in every build; the generator only removes files it wrote itself.

```ts
import { BikeLeasingProcessApi } from "./process/BikeLeasingProcessApi.js";
import { ServiceTasks } from "./process/ServiceTasks.js";

// a worker subscribes to the job type
zeebe.createWorker({ taskType: ServiceTasks.MIRAVELO_VALIDATE_APPLICATION, taskHandler: validate });

// a frontend shows the step a leasing request is in
const step = BikeLeasingProcessApi.FlowNodes.ServiceTaskValidateApplication;
showLabel(step.name); // "Validate application"
```

What the generated TypeScript looks like in detail: [TypeScript specifics](/guide/generated-api#typescript-specifics).

The tool writes every language, not only TypeScript: `--outputLanguage CSHARP --packagePath MiraVelo.Process` writes C#. A .NET project rather lets `dotnet build` do it, see [.NET](/getting-started/dotnet).

## Commands

| Command | Does what | Same as |
|---|---|---|
| `generate-api` | Generates the Process API of every BPMN file | Gradle `generateBpmnModelApi`, Maven `generate-bpmn-api` |
| `validate` (experimental) | Checks every BPMN file against the [validation rules](/validate/) | Gradle `validateBpmnModels`, Maven `validate-bpmn` |

`bpmn-to-code <command> --help` lists the flags of a command, `bpmn-to-code --version` prints the version.

## Flags

The flags carry the names of the plugin parameters, see [Configuration](/guide/configuration). There are no defaults: a call states everything it relies on.

| Flag | Commands | Description |
|---|---|---|
| `--baseDir` | all | Directory the file pattern is resolved against |
| `--filePattern` | all | Glob of the BPMN files, relative to `baseDir` |
| `--processEngine` | all | `ZEEBE`, `CAMUNDA_7` or `OPERATON` |
| `--outputFolderPath` | `generate-api` | Source root the files are written to |
| `--outputLanguage` | `generate-api` | `KOTLIN`, `JAVA`, `CSHARP` or `TYPESCRIPT` |
| `--packagePath` | `generate-api` | Package or namespace of the code; for TypeScript only an optional directory below `outputFolderPath` |
| `--failOnWarning` | `validate` | Treat warnings as failures |
| `--disabledRules` | `validate` | Comma-separated ids of rules to skip |

Relative paths resolve against the directory the tool is started in. npm runs scripts in the directory of `package.json`.

## Exit codes

| Code | Meaning |
|---|---|
| `0` | Done |
| `1` | The models failed validation or could not be read or generated; the findings are printed |
| `2` | Wrong or missing flags; the usage is printed |

## In CI

Run the script in your pipeline like any other build step. To fail a pull request on modeling mistakes before anything is generated, add a validation step:

```bash
npx bpmn-to-code validate --baseDir bpmn --filePattern "*.bpmn" --processEngine ZEEBE --failOnWarning
```
