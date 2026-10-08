# ⚙️ Configuration

Every parameter of the three Gradle tasks and Maven goals. Setup snippets are on the [Gradle](/getting-started/gradle) and [Maven](/getting-started/maven) pages.

| | Gradle task | Maven goal |
|---|---|---|
| [Process API](/guide/generated-api) | `generateBpmnModelApi` (`GenerateBpmnModelsTask`) | `generate-bpmn-api` |
| [JSON export](/surface/json) | `generateBpmnModelJson` (`GenerateBpmnJsonTask`) | `generate-bpmn-json` |
| [Validation](/validate/) (experimental) | `validateBpmnModels` (`ValidateBpmnModelsTask`) | `validate-bpmn` |

The Gradle task classes live in `io.miragon.bpmn.adapter`. A Gradle property without a default has to be set, or the build fails naming it. The Maven goals are bound to no lifecycle phase; each parameter is also a user property of the same name (`-DprocessEngine=ZEEBE`).

## Common parameters

All three tasks and goals take these.

| Parameter | Gradle default | Maven default | Description |
|-----------|----------------|---------------|-------------|
| `baseDir` | — | `.` | Directory `filePattern` is resolved against |
| `filePattern` | — | `src/main/resources/*.bpmn` | Glob selecting the BPMN files, relative to `baseDir` |
| `processEngine` | — | — | `ZEEBE`, `CAMUNDA_7` or `OPERATON`; see [Engines](/engines/) |

**Paths.** In Gradle, a relative `baseDir` or `outputFolderPath` resolves against the project directory. In Maven it resolves against the directory Maven is started in, so use `${project.basedir}`.

**File pattern.** `*` matches within one directory, `**/` any number of directories, including none: `src/main/resources/**/*.bpmn` also finds files directly in `src/main/resources`. Files behind a symlinked directory are not read. There is no exclude; see filtering files for [Gradle](/getting-started/gradle#filtering-files) and [Maven](/getting-started/maven#filtering-files).

## Process API

| Parameter | Gradle default | Maven default | Description |
|-----------|----------------|---------------|-------------|
| `outputFolderPath` | — | `src/main/kotlin` | Source root the code is written to; the package directories are created below it |
| `packagePath` | — | `de.emaarco.generated` | Package (C#: namespace) of the generated code. One package per task or execution: two runs sharing a package overwrite each other's [shared definitions](/guide/generated-api#shared-definitions) and remove each other's Process APIs as stale |
| `outputLanguage` | — | `KOTLIN` | `KOTLIN`, `JAVA` or `CSHARP` (experimental) |

## JSON export

| Parameter | Gradle default | Maven default | Description |
|-----------|----------------|---------------|-------------|
| `outputFolderPath` | — | `src/main/resources/bpmn-json` | Directory the `.json` files are written to |

## Validation

| Parameter | Gradle default | Maven default | Description |
|-----------|----------------|---------------|-------------|
| `failOnWarning` | `false` | `false` | Treat warnings as failures |
| `disabledRules` | empty | empty | Ids of [rules](/validate/) to skip |

Both generating tasks run the built-in rules as well and fail on an error. They have neither parameter.

## Settings in the BPMN model

| Extension property | On | Description |
|--------------------|----|-------------|
| `variantName` | the process | Leads the name of what is generated from the file, which lets [several files declare the same process id](/guide/modeling#several-files-one-process-id) |
| `additionalInputVariables`, `additionalOutputVariables` | any flow node (Camunda 7, Operaton) | Declares [variables](/guide/modeling#variables) an element cannot express otherwise |

They live in the model, so they apply the same way in Gradle, Maven and the [web app](/web/).

## Output Languages

| Value | Generated | Runtime |
|-------|-----------|---------|
| `KOTLIN` | `object` with nested objects | `io.miragon:bpmn-to-code-runtime` |
| `JAVA` | `final class` with nested static classes | `io.miragon:bpmn-to-code-runtime` |
| `CSHARP` | `static class` with the same content | none, the types are inlined into each file |

The Gradle plugin adds the runtime [when the `java` plugin is applied](/getting-started/gradle#runtime-dependency); with Maven you [declare it yourself](/getting-started/maven#runtime-dependency).

::: warning Experimental
C# output is experimental and may change in a minor release. See [C# specifics](/guide/generated-api#c-specifics).
:::
