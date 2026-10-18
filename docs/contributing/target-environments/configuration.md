# Ideas for Configuration

Decided: **idea 1, flags only**, with idea 5 as it is today. A file of our own or a section in the ecosystem's file can be added later without breaking the flags. This page collects the ways the tool could be configured once it runs outside Gradle and Maven, each with tools that already work that way.

Today the settings are Gradle task properties, Maven parameters and the body of a web request, with the same names everywhere. The settings themselves are few: where the BPMN files are, which engine, which output language, where the output goes, and for Kotlin, Java and C# the package or namespace. All of them are listed in the [configuration guide](/guide/configuration).

## The ideas at a glance

| Idea | In one sentence | Known from |
|---|---|---|
| [1. Flags only](#_1-flags-only) | Everything is an argument of the command | `protoc`, `esbuild` |
| [2. An own file](#_2-an-own-file) | A `bpmn-to-code.json` in the project | `.bpmnlintrc`, `tsconfig.json`, `biome.json`, `buf.gen.yaml` |
| [3. File and flags](#_3-file-and-flags) | The file is the normal case, a flag overrides one value | `tsc`, ESLint, Biome |
| [4. A section in the ecosystem's file](#_4-a-section-in-the-ecosystem-s-file) | Settings live in `package.json`, the `.csproj`, `pyproject.toml` | Prettier, Ruff, MSBuild, and our Gradle and Maven plugins today |
| [5. In the BPMN model](#_5-in-the-bpmn-model) | A setting that shapes the output is a property of the model | `variantName` today |

The ideas do not exclude each other. Idea 5 is already policy and stays; the question is which of 1 to 4 comes on top.

## 1. Flags only

The command takes every setting as an argument, with the names the Gradle and Maven plugins use today.

```bash
bpmn-to-code generate --baseDir bpmn --filePattern "*.bpmn" --processEngine ZEEBE \
  --outputLanguage TYPESCRIPT --outputFolderPath src/process
```

**Known from:** `protoc --java_out=build/gen order.proto` takes inputs, output language and output folder as arguments and knows no configuration file. `esbuild app.ts --bundle --outfile=out.js` works the same way.

| For | Against |
|---|---|
| Nothing new to learn: the names exist already | The command gets long |
| No rules about which value wins | A project that generates two languages needs two calls, and nothing ties them together |
| Works the same in every environment, and travels through a shell script or a CI step | The settings are spread over `package.json` scripts, CI files and build scripts |

## 2. An own file

One file in the project holds the settings. The command needs no arguments.

```json
{
  "engine": "ZEEBE",
  "input": "bpmn/*.bpmn",
  "targets": [
    { "language": "TYPESCRIPT", "output": "src/process" },
    { "language": "KOTLIN", "output": "build/generated", "package": "io.miravelo.process" }
  ]
}
```

**Known from:**

| Tool | File | What it shows |
|---|---|---|
| bpmnlint | `.bpmnlintrc` | A BPMN tool that teams already configure through a file in the repository |
| TypeScript | `tsconfig.json` | `tsc` without arguments reads the file of the current directory |
| Biome | `biome.json` | One file for a tool that is not written in JavaScript but is installed through npm |
| Buf (Protocol Buffers) | `buf.gen.yaml` | A list of generators, each with its own output folder: the closest match to `targets` above |
| OpenAPI Generator | `openapitools.json` | The npm wrapper of a Java generator keeps its settings in a JSON file |

| For | Against |
|---|---|
| One place, the same in every environment | A new public contract: the file format has to stay compatible |
| Several targets in one file fit a repository in which one model feeds a Kotlin service and a TypeScript frontend | Gradle has to read the file while configuring the build, or up-to-date checks and the build cache stop working |
| Editors can validate and complete it from a JSON schema | Maven's parameter defaults have to go, or they always win over the file |
| | The web app cannot read a file and needs the same structure in its request |
| | One Gradle task is one language and one output folder today; a list of targets has to map to several tasks |

### A repository with several modules

A Gradle build with several modules has several generate tasks, often more than one per module, each with its own models, package and output folder. One file cannot replace that without naming every module and mapping every entry back to a task.

It does not have to. In Gradle and Maven the build script already is the configuration, per module and per task, and stays it (idea 4). The file is for environments that have no build script with tasks. For a repository with several packages there, two layouts are possible:

| Layout | Known from | Consequence |
|---|---|---|
| One file per package, paths relative to the file | `tsconfig.json` in every package of a workspace | Matches "per module". Each package is self-contained; nothing ties the files together |
| One file at the root that lists every target | `buf.gen.yaml` | One place to look. Every package depends on a central file, and paths are long |

Several tasks of one module become several entries in `targets`.

## 3. File and flags

The file is the normal case. A flag overrides a single value for one run.

```bash
bpmn-to-code generate                         # everything from bpmn-to-code.json
bpmn-to-code generate --processEngine OPERATON  # one value replaced
```

**Known from:** `tsc --outDir build` overrides `outDir` of `tsconfig.json`; ESLint and Biome let command-line options win over their file in the same way.

| For | Against |
|---|---|
| Most convenient in daily use | Needs rules: flag before file before default, and which directory a relative path belongs to (the file's or the one the command runs in) |
| A CI job can change one value without a second file | With several targets in the file it is unclear which one a flag such as `--outputFolderPath` means |
| | Everything listed against idea 2 |

## 4. A section in the ecosystem's file

No file of our own. The settings sit where the ecosystem keeps tool settings anyway.

```json
{
  "name": "miravelo-workers",
  "bpmnToCode": {
    "engine": "ZEEBE",
    "input": "bpmn/*.bpmn",
    "language": "TYPESCRIPT",
    "output": "src/process"
  }
}
```

**Known from:** Prettier reads a `prettier` key in `package.json`, Ruff a `[tool.ruff]` table in `pyproject.toml`, and .NET tools take MSBuild properties from the `.csproj`. Our Gradle and Maven plugins are this idea already: their settings live in `build.gradle.kts` and `pom.xml`.

| For | Against |
|---|---|
| Feels native in each ecosystem, no extra file | One reader per ecosystem to write and keep the same |
| Continues what Gradle and Maven do today | A repository with a Kotlin service and a TypeScript frontend configures the same models in two places |
| | The command-line tool has to know about `package.json`, `.csproj` and `pyproject.toml` |

## 5. In the BPMN model

A setting that changes what is generated from a model belongs to the model, as an extension property. This is policy today ([ADR 001](../adr/001-product-surfaces-and-distribution)) and the reason there are so few settings: `variantName` lives in the BPMN file and therefore works the same in Gradle, Maven and the web app.

| For | Against |
|---|---|
| Identical in every entry point by construction | Only fits settings that belong to one model, not "where do the files go" |
| The model stays the single source for its generated API | Needs a modeler to set it |

None of ideas 1 to 4 should take over a setting of this kind.

## What holds for every idea

- **The same everywhere.** A setting that exists in one entry point only is a defect by the same ADR. A file or section that the command-line tool reads has to mean the same in Gradle, Maven and the web app, or those have to be left as they are on purpose.
- **Validation settings stay with validation.** Generation takes no validation settings today ([ADR 010](../adr/010-validation)); a file must not bring them in through the back door.
- **No naming options.** How identifiers are derived is not configurable, in any idea.

Back to the [overview](./).
