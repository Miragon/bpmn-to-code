# bpmn-to-code-cli

The command-line tool of [bpmn-to-code](https://github.com/Miragon/bpmn-to-code): generates the Process API from BPMN files and validates them, without Gradle or Maven. It calls the same generator as the Gradle and Maven plugins, so the output is identical.

Experimental: commands, flags and exit codes may still change in a minor release.

## Install

Every [GitHub release](https://github.com/Miragon/bpmn-to-code/releases) carries one zip per platform: `linux-x64`, `linux-arm64`, `darwin-arm64` and `win32-x64`. Each contains its own Java runtime, so no Java installation is needed.

```bash
curl -fsSLO https://github.com/Miragon/bpmn-to-code/releases/download/v<version>/bpmn-to-code-<version>-linux-x64.zip
unzip bpmn-to-code-<version>-linux-x64.zip
bpmn-to-code-<version>-linux-x64/bin/bpmn-to-code --version
```

On Windows the command is `bin\bpmn-to-code.cmd`. The archives are not signed: macOS blocks a zip downloaded in a browser until you run `xattr -dr com.apple.quarantine bpmn-to-code-<version>-darwin-arm64`; `curl` does not set that flag. `gh attestation verify <zip> --repo Miragon/bpmn-to-code` checks that an archive was built by this repository's release workflow.

## Build from source

```bash
./gradlew :bpmn-to-code-cli:cliArchive
```

This packs the tool with a runtime linked by `jlink` into `bpmn-to-code-cli/build/cli-archive/bpmn-to-code-<version>-<platform>.zip`, for the platform the build runs on. `./gradlew :bpmn-to-code-cli:installDist` builds it without a runtime into `build/install`; its scripts need Java 21 or newer.

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
