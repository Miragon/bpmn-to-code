# bpmn-to-code Agent Skills

**Let Claude Code set up bpmn-to-code and write your workers, process adapters and tests against the generated Process API.**

A Claude Code plugin for [bpmn-to-code](https://github.com/Miragon/bpmn-to-code) with eight skills and one subagent, for Kotlin and Java on Camunda 8 / Zeebe, the Camunda 7 family (Camunda 7, CIB seven, Operaton, Fluxnova) with embedded delegates or remote external tasks, and every engine with a [process-engine-api](https://github.com/bpm-crafters/process-engine-api) adapter. Each skill does one thing for one element. The skills are in beta: names and instructions may change.

## Install

```text
/plugin marketplace add Miragon/bpmn-to-code
/plugin install bpmn-to-code@bpmn-to-code
```

The plugin brings Miragon's [bpmn-cli](https://github.com/Miragon/bpmn-cli) plugin as a dependency, so the skills read models by id instead of raw XML when `bpmn` is installed or Node.js is available; otherwise they read the XML.

## Get started

Call a skill as a slash command, or describe what you need and Claude picks the skill:

```text
/bpmn-to-code:setup-bpmn-to-code
/bpmn-to-code:create-worker serviceTask_orderBike
/bpmn-to-code:create-process-adapter message miravelo.contractSigned
/bpmn-to-code:test-process bikeLeasing "bike unavailable"
```

## Skills

| Skill                                      | Does                                                                                                                                   |
| ------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------- |
| `setup-bpmn-to-code`                       | Sets up the Gradle plugin, the Maven plugin or the CLI, with the runtime, an optional model validation test and an optional GitHub Actions check for a stale API. |
| `migrate-to-bpmn-to-code-apis`             | Replaces hardcoded BPMN strings with references to the generated Process API, or with `--from-5x` rewrites 5.x references to the 6.x `FlowNodes`.     |
| `create-worker`                            | Writes the handler for one service task: Zeebe job worker, `JavaDelegate`, external task or process-engine-api worker.               |
| `test-worker`                              | Writes the unit test for one handler.                                                                                                  |
| `create-process-adapter`                   | Adds one call to the engine (start, message, signal, user task) as an outbound port and its adapter method.                           |
| `test-process-adapter`                     | Writes the unit test for one process adapter method.                                                                                   |
| `test-process`                             | Writes one process test that asserts the walked path with a typed `ProcessPath`.                                                       |
| `generate-rule-to-enforce-bpmn-styleguide` | Turns one rule of a `BPMN_STYLE_GUIDE.md` into a Kotlin validation rule and registers it in the model validation test.                |

The subagent `process-reviewer` reviews one process against its code and tests and reports what it finds without fixing it; call it with `@bpmn-to-code:process-reviewer`.

## Help and feedback

- **Documentation:** [Agent Skills](https://miragon.github.io/bpmn-to-code/skills/)
- **Bugs and ideas:** [open an issue](https://github.com/Miragon/bpmn-to-code/issues)

## License

[MIT](../LICENSE)
