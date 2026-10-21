# bpmn-to-code Skills Plugin

Claude Code plugin with Agent Skills for [bpmn-to-code](https://github.com/Miragon/bpmn-to-code). They cover two things:
- setting up the generator
- writing and testing engine code against the generated Process API

Each skill does one thing for one element. Engine- and language-specific code lives in each skill's `resources/` folder. It comes as one file per style and language, for example `zeebe-kotlin.md` or `c7-delegate-java.md`.

## Installation

```bash
# 1. Register the marketplace (one-time)
/plugin marketplace add Miragon/bpmn-to-code

# 2. Install the plugin; it brings Miragon's bpmn-cli plugin as a dependency
/plugin install bpmn-to-code@bpmn-to-code
```

## Included Skills

| Skill | Description |
|-------|-------------|
| `setup-bpmn-to-code` | Set up the Gradle plugin, the Maven plugin or the CLI, with the runtime, an optional model validation test and an optional GitHub Actions drift check. |
| `migrate-to-bpmn-to-code-apis` | Replace hardcoded BPMN strings with references to the generated Process API, or rewrite 5.x references to the 6.0 node-centric `FlowNodes`. |
| `create-worker` | Write the handler for one service task: Zeebe job worker, `JavaDelegate`, external task or process-engine-api worker. |
| `test-worker` | Write the unit test for one handler. |
| `create-process-adapter` | Add one call to the engine (start, message, signal, user task) as an outbound port and its adapter method. |
| `test-process-adapter` | Write the unit test for one process adapter method. |
| `test-process` | Write one process test that asserts the walked path with a typed `ProcessPath`. |
| `generate-rule-to-enforce-bpmn-styleguide` | Generate a Kotlin `SingleModelValidationRule` for one rule of a `BPMN_STYLE_GUIDE.md` and register it in the model validation test. The style guide skills themselves live in [Miragon/bpmnlint-rules](https://github.com/Miragon/bpmnlint-rules). |

Covered engine styles:

| Prefix | Engines |
|---|---|
| `zeebe` | Camunda 8 / Zeebe |
| `c7-delegate` | Camunda 7, CIB seven, Operaton, Fluxnova with an embedded engine |
| `c7-external-task` | Camunda 7, CIB seven, Operaton with a remote engine |
| `process-engine-api` | every engine with a bpm-crafters process-engine-api adapter |

## Usage

Once installed, skills are available as slash commands:

```
/bpmn-to-code:setup-bpmn-to-code
/bpmn-to-code:create-worker serviceTask_orderBike
/bpmn-to-code:create-process-adapter message miravelo.contractSigned
/bpmn-to-code:test-process bikeLeasing "bike unavailable"
```

Or describe what you need and Claude will invoke the appropriate skill automatically. To automate a whole process, plan it and call the skills once per element.

## Included Subagent

| Subagent | Description |
|----------|-------------|
| `process-reviewer` | Reviews one process against its glue code and tests. Reports, does not fix. |

Invoke with `@bpmn-to-code:process-reviewer` or let Claude delegate automatically.

## Versioning

`plugin.json` carries the bpmn-to-code version and is bumped by release-please with every release. A resource file describes the API of that release.
