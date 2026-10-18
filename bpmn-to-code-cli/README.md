# bpmn-to-code-cli

The command-line tool of [bpmn-to-code](https://github.com/Miragon/bpmn-to-code): generates the Process API from BPMN files and validates them, without Gradle or Maven. It calls the same generator as the Gradle and Maven plugins, so the output is identical.

Not published yet. Its commands and flags may still change.

## Build

```bash
./gradlew :bpmn-to-code-cli:installDist
```

This writes the tool to `bpmn-to-code-cli/build/install/bpmn-to-code`: start scripts in `bin/` (`bpmn-to-code` and `bpmn-to-code.bat`) and the jars in `lib/`. `./gradlew :bpmn-to-code-cli:distZip` packs the same into a zip. The scripts need Java 21 or newer, found through `JAVA_HOME` or the `PATH`.

## Usage

```bash
bpmn-to-code generate-api \
  --baseDir src/main/resources \
  --filePattern "**/*.bpmn" \
  --processEngine ZEEBE \
  --outputLanguage KOTLIN \
  --outputFolderPath build/generated/bpmn \
  --packagePath io.miravelo.process

bpmn-to-code validate \
  --baseDir src/main/resources \
  --filePattern "**/*.bpmn" \
  --processEngine ZEEBE
```

`bpmn-to-code --help` and `bpmn-to-code <command> --help` list the flags, `bpmn-to-code --version` prints the version.

## Commands

| Command | Same as Gradle task | Same as Maven goal |
|---|---|---|
| `generate-api` | `generateBpmnModelApi` | `generate-bpmn-api` |
| `validate` (experimental) | `validateBpmnModels` | `validate-bpmn` |

The flags carry the names of the plugin parameters, see [Configuration](https://miragon.github.io/bpmn-to-code/guide/configuration). Unlike in Maven, there are no defaults: every flag except `--failOnWarning` and `--disabledRules` is required. Relative paths resolve against the directory the tool is started in.

| Flag | Commands | Value |
|---|---|---|
| `--baseDir` | both | Directory `--filePattern` is resolved against |
| `--filePattern` | both | Glob of the BPMN files, relative to `--baseDir` |
| `--processEngine` | both | `ZEEBE`, `CAMUNDA_7` or `OPERATON` |
| `--outputLanguage` | `generate-api` | `KOTLIN`, `JAVA` or `CSHARP` |
| `--outputFolderPath` | `generate-api` | Source root the code is written to |
| `--packagePath` | `generate-api` | Package of the generated code, the namespace for C# |
| `--failOnWarning` | `validate` | Treat warnings as failures |
| `--disabledRules` | `validate` | Comma-separated ids of rules to skip |

## Exit codes

| Code | Meaning |
|---|---|
| `0` | Success |
| `1` | The models fail validation or cannot be read or generated |
| `2` | Wrong call: unknown command, missing flag, unknown value |

Findings and errors go to standard error.
