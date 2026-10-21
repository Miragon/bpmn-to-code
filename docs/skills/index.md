# Agent Skills

::: warning Beta
Agent Skills are in beta. Skill names and instructions may change. [Leave feedback](https://github.com/Miragon/bpmn-to-code/issues) if you use them.
:::

bpmn-to-code ships a [Claude Code](https://docs.anthropic.com/en/docs/claude-code) plugin with eight skills and one subagent. They are optional; the build plugins work without them.

Each skill does one thing for one element. To automate a whole process, plan it and call the skills once per service task, message or path. Engine- and language-specific code lives in resource files the skill reads only when it applies. The covered styles:
- Zeebe
- the Camunda 7 family (Camunda 7, CIB seven, Operaton, Fluxnova) with embedded delegates or remote external tasks
- the [process-engine-api](https://github.com/bpm-crafters/process-engine-api)

Each style comes in Kotlin and Java, modelled on the [MiraVelo blueprints](https://github.com/miragon-blueprints).

## Skills

**Setup**

| Skill | What it does |
|-------|--------------|
| `setup-bpmn-to-code` | Sets up the Gradle plugin, the Maven plugin or the command-line tool. Detects build tool, engine, language and BPMN files, adds the runtime and optionally a model validation test and a GitHub Actions job that fails when the committed API is out of date. |
| `migrate-to-bpmn-to-code-apis` | Replaces hardcoded BPMN strings with references to the generated Process API. With `--from-5x` it rewrites 5.x references (`Elements`, `Variables`, `CallActivities`, `Timers`) to the 6.x `FlowNodes`. |

**Engine code**

| Skill | What it does |
|-------|--------------|
| `create-worker` | Writes the handler for one service task (job worker, `JavaDelegate`, external task or `@ProcessEngineWorker`) that calls one use case, with job type, variables and error codes from the generated API. |
| `test-worker` | Writes the unit test for one handler: domain values in, generated variable names out, BPMN errors and technical failures. |
| `create-process-adapter` | Adds one call to the engine (start, message, signal, user task) as an outbound port and its implementation in the process adapter. |
| `test-process-adapter` | Writes the unit test for one adapter method: generated names, correlation and variables of the engine command. |
| `test-process` | Writes one process test that runs a scenario on a test engine and asserts the path with a typed [`ProcessPath`](/guide/process-path). |

**Style guide**

| Skill | What it does |
|-------|--------------|
| `generate-rule-to-enforce-bpmn-styleguide` | Turns one rule of a `BPMN_STYLE_GUIDE.md` into a Kotlin `SingleModelValidationRule` for the [testing module](/validate/testing) and registers it in the model validation test. |

Writing the style guide and checking models against it are skills of [Miragon/bpmnlint-rules](https://github.com/Miragon/bpmnlint-rules).

## Subagent

| Subagent | What it does |
|----------|--------------|
| `process-reviewer` | Reviews one process against its code: the generated API is current and checked in CI, every job type has one handler, error codes match, messages and user tasks go through the process adapter, every branch has a process test. Reports, does not fix. |

## bpmn-cli

Several skills read models with [bpmn-cli](https://github.com/Miragon/bpmn-cli) instead of raw XML. The plugin declares Miragon's `bpmn-cli` plugin as a dependency, so installing bpmn-to-code installs it too.

The command itself is not bundled:
- **Your own installation wins:** a `bpmn` you installed with `npm install -g @miragon/bpmn-cli` or `brew install miragon/tap/bpmn-cli` is used as is.
- **Without one:** the skills fall back to `npx -y @miragon/bpmn-cli`, which needs Node.js.
- **Without Node.js:** they read the XML.

If you already installed `bpmn-cli` from another marketplace, such as Miragon's, you have its skill twice. Disable one of them in `/plugin`.

## Installation

### Claude Code plugin (recommended)

```bash
/plugin marketplace add Miragon/bpmn-to-code
/plugin install bpmn-to-code@bpmn-to-code
```

The skills are then available as slash commands, the subagent by mention:

```
/bpmn-to-code:setup-bpmn-to-code
/bpmn-to-code:create-worker serviceTask_orderBike
/bpmn-to-code:migrate-to-bpmn-to-code-apis --from-5x
@bpmn-to-code:process-reviewer
```

You can also describe what you need and let Claude pick the skill.

The plugin version follows the bpmn-to-code release, so the skills describe the API of that release.

### Other agents: npx skills

The skills live in [`bpmn-to-code-skills/skills/`](https://github.com/Miragon/bpmn-to-code/tree/main/bpmn-to-code-skills/skills) and can be installed with [`npx skills`](https://github.com/vercel-labs/skills):

```bash
# all skills
npx skills add https://github.com/Miragon/bpmn-to-code

# a single one
npx skills add https://github.com/Miragon/bpmn-to-code/tree/main/bpmn-to-code-skills/skills/setup-bpmn-to-code
```

This way installs no dependency and no subagent. Install bpmn-cli yourself if you want the skills to read models through it.
