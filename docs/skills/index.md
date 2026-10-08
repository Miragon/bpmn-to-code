# 🧠 AI Agent Skills

::: warning Beta
Agent skills are in beta. Skill names and instructions may change. [Leave feedback](https://github.com/Miragon/bpmn-to-code/issues) if you use them.
:::

bpmn-to-code ships a [Claude Code](https://docs.anthropic.com/en/docs/claude-code) plugin with seven skills and one subagent. They are optional; the build plugins work without them.

## Skills

| Skill | What it does |
|-------|--------------|
| `setup-bpmn-to-code-gradle` | Sets up the Gradle plugin in an existing project: detects project structure, BPMN files and output language. |
| `setup-bpmn-to-code-maven` | Sets up the Maven plugin in an existing project by adding the plugin configuration to `pom.xml`. |
| `migrate-to-bpmn-to-code-apis` | Replaces hardcoded BPMN strings with references to the generated Process API. With `--from-5x` it rewrites 5.x references (`Elements`, `Variables`, `CallActivities`, `Timers`) to the 6.x `FlowNodes`. |
| `build-bpmn-styleguide` | Creates a `BPMN_STYLE_GUIDE.md` with your team's conventions for naming, IDs and allowed elements in an interactive session. |
| `validate-bpmn-style` | Checks BPMN files against the `BPMN_STYLE_GUIDE.md` and reports violations with explanations. |
| `generate-rules-to-enforce-bpmn-styleguide` | Generates Kotlin `SingleModelValidationRule` implementations for every style guide rule that can be checked automatically, for use with the [testing module](/validate/testing). |
| `generate-rule-to-enforce-bpmn-styleguide` | The same for a single rule — useful while drafting one. |

## Subagent

| Subagent | What it does |
|----------|--------------|
| `bpmn-styleguide-validator` | Runs `validate-bpmn-style` in its own context and returns a violation report per file. Use it to keep BPMN XML out of the main conversation or to validate many files in parallel. |

## Installation

### Claude Code plugin (recommended)

```bash
/plugin marketplace add Miragon/bpmn-to-code
/plugin install bpmn-to-code@bpmn-to-code
```

The skills are then available as slash commands, the subagent by mention:

```
/bpmn-to-code:setup-bpmn-to-code-gradle
/bpmn-to-code:migrate-to-bpmn-to-code-apis --from-5x
@bpmn-to-code:bpmn-styleguide-validator
```

You can also describe what you need and let Claude pick the skill.

### Other agents: npx skills

The skills live in [`bpmn-to-code-skills/skills/`](https://github.com/Miragon/bpmn-to-code/tree/main/bpmn-to-code-skills/skills) and can be installed with [`npx skills`](https://github.com/vercel-labs/skills):

```bash
# all skills
npx skills add https://github.com/Miragon/bpmn-to-code

# a single one
npx skills add https://github.com/Miragon/bpmn-to-code/tree/main/bpmn-to-code-skills/skills/setup-bpmn-to-code-gradle
```
