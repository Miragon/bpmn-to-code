# bpmn-to-code CLI

[![GitHub release](https://img.shields.io/github/v/release/Miragon/bpmn-to-code?label=release&color=335DE5)](https://github.com/Miragon/bpmn-to-code/releases/latest)

**Generate the typed Process API from your BPMN models without Gradle or Maven.**

`bpmn-to-code` is the command-line tool of [bpmn-to-code](https://github.com/Miragon/bpmn-to-code): it generates and validates with the same generator as the Gradle and Maven plugins, so the output is identical. It is experimental: commands, flags and exit codes may still change in a minor release.

## Install

- **Homebrew** (macOS, Linux): `brew install miragon/tap/bpmn-to-code`
- **Release zip:** every [GitHub release](https://github.com/Miragon/bpmn-to-code/releases) carries one zip for `linux-x64`, `linux-arm64`, `darwin-arm64`, `darwin-x64` and `win32-x64`, each with its own Java runtime.
- **.NET tool:** `Miragon.BpmnToCode` as a local tool, which needs the .NET 10 SDK and Java 21 (not yet published, see [the tool on its own](https://miragon.github.io/bpmn-to-code/getting-started/dotnet#the-tool-on-its-own)).
- **npm:** `@miragon/bpmn-to-code`, which needs Java 21 (not yet published, see the [TypeScript guide](https://miragon.github.io/bpmn-to-code/getting-started/typescript)).

From a zip:

```bash
curl -fsSLO https://github.com/Miragon/bpmn-to-code/releases/download/v<version>/bpmn-to-code-<version>-linux-x64.zip
unzip bpmn-to-code-<version>-linux-x64.zip
bpmn-to-code-<version>-linux-x64/bin/bpmn-to-code --version
```

Put the `bin` folder on your `PATH` to call `bpmn-to-code` directly; on Windows the command is `bin\bpmn-to-code.cmd`. The archives are not signed: macOS blocks a zip downloaded in a browser until you run `xattr -dr com.apple.quarantine bpmn-to-code-<version>-darwin-arm64`; Homebrew and `curl` do not set that flag. `gh attestation verify <zip> --repo Miragon/bpmn-to-code` checks that an archive was built by this repository's release workflow.

## Get started

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

`bpmn-to-code --help` and `bpmn-to-code <command> --help` list the flags. The generated code compiles against the runtime package of its language: `io.miragon:bpmn-to-code-runtime` for Kotlin and Java, `Miragon.BpmnToCode.Runtime` for C#, `@miragon/bpmn-to-code-runtime` for TypeScript, in the same version as the tool.

## Commands and flags

| Command                   | Same as Gradle task    | Same as Maven goal  |
| ------------------------- | ---------------------- | ------------------- |
| `generate-api`            | `generateBpmnModelApi` | `generate-bpmn-api` |
| `validate` (experimental) | `validateBpmnModels`   | `validate-bpmn`     |

The flags carry the names of the plugin parameters, see [Configuration](https://miragon.github.io/bpmn-to-code/guide/configuration). As in the Gradle and Maven plugins, every flag except `--failOnWarning` and `--disabledRules` is required. Relative paths resolve against the directory the tool is started in.

| Flag                 | Commands       | Value                                                                      |
| -------------------- | -------------- | -------------------------------------------------------------------------- |
| `--baseDir`          | both           | Directory `--filePattern` is resolved against                              |
| `--filePattern`      | both           | Glob of the BPMN files, relative to `--baseDir`                            |
| `--processEngine`    | both           | `ZEEBE`, `CAMUNDA_7` or `OPERATON`                                         |
| `--outputLanguage`   | `generate-api` | `KOTLIN`, `JAVA`, `CSHARP` (experimental) or `TYPESCRIPT` (experimental)   |
| `--outputFolderPath` | `generate-api` | Source root the code is written to                                         |
| `--packagePath`      | `generate-api` | Package of the generated code, the namespace for C#                        |
| `--failOnWarning`    | `validate`     | Treat warnings as failures                                                 |
| `--disabledRules`    | `validate`     | Comma-separated ids of [rules](https://miragon.github.io/bpmn-to-code/validate/) to skip |

| Exit code | Meaning                                                   |
| --------- | --------------------------------------------------------- |
| `0`       | Success                                                   |
| `1`       | The models fail validation or cannot be read or generated |
| `2`       | Wrong call: unknown command, missing flag, unknown value  |

Findings and errors go to standard error.

## Help and feedback

- **Documentation:** [miragon.github.io/bpmn-to-code](https://miragon.github.io/bpmn-to-code/)
- **Bugs and ideas:** [open an issue](https://github.com/Miragon/bpmn-to-code/issues)
- **Building the tool from source:** [CONTRIBUTING.md](../CONTRIBUTING.md#building-the-command-line-tool)

## License

[MIT](../LICENSE)
