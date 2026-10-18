# 15. Agent skills

## Context

The plugin in `bpmn-to-code-skills/` began as a setup aid: one skill per build tool, plus a style guide workflow. The code users write against the generated API was taught by skills inside single example repositories: workers, the calls into the engine, and the tests around both. Those skills were tied to one engine and copied from repository to repository. The [MiraVelo blueprints](https://github.com/miragon-blueprints) show that this code has the same shape on every engine; only the engine mechanism differs. A skill that looped over another skill (generate all rules) added nothing that calling the single skill repeatedly in plan mode does not give.

## Decision

**One skill per task, for one element.**
- A skill writes one worker, one adapter call, one test or one rule.
- Automating a process is a plan that calls the skills once per element.
- There is no orchestrating skill.

**Engine knowledge lives in resources, one file per style and language.** The SKILL.md is engine-neutral: it detects the style from the build and the existing code, then reads `resources/<style>-<kotlin|java>.md`. The styles:

| Style | Engines |
|---|---|
| `zeebe` | Camunda 8 |
| `c7-delegate` | Camunda 7, CIB seven, Operaton and Fluxnova with an embedded engine |
| `c7-external-task` | the same with a remote engine |
| `process-engine-api` | every engine with a bpm-crafters adapter |

The engines of the Camunda 7 family differ only in their import root, so they share a file. Each resource follows the blueprint of its style and links to it.

**Resources stay inside each skill.** A shared folder at the plugin root would need `${CLAUDE_PLUGIN_ROOT}`, which `npx skills` installs do not have. Several skills therefore have a file for the same style, each with only what that skill needs.

**Style guide skills live elsewhere.** Writing a `BPMN_STYLE_GUIDE.md` and checking models against it does not depend on bpmn-to-code, so those skills live in the plugin of [Miragon/bpmnlint-rules](https://github.com/Miragon/bpmnlint-rules). Only turning a rule of the guide into a rule for `bpmn-to-code-testing` stays here.

**Generated names only.** Every skill takes job types, variables, messages, signals and error codes from the generated API and never writes them as strings.

**bpmn-cli is a plugin dependency, not a bundled binary.**
- The plugin declares Miragon's `bpmn-cli` plugin as a dependency, and the marketplace lists that plugin so the dependency resolves.
- Skills read models with `bpmn show` instead of raw XML.
- A user's own `bpmn` on the PATH is used. Otherwise the skills fall back to `npx -y @miragon/bpmn-cli`, and without Node.js to the XML.

**The plugin version is the library version.** release-please bumps `plugin.json` with every release, so the installed skills describe the API of that release.

## Consequences

- A change to the generated API, the runtime or the testing library may need a change to the resources. `bpmn-to-code-validate-docs` lists the shipped skills, but no test compiles the snippets.
- Supporting a new engine style means writing one resource file per skill and language.
- Skill fixes reach users with the next release, not with the next commit.
- Users who installed bpmn-cli from another marketplace get its skill twice and have to disable one.
- `claude plugin validate --strict` checks the plugin and the marketplace on every pull request that touches them.

## Rejected alternatives

- **One skill per engine** (`create-zeebe-worker`, …). The rules about generated names, ports and tests would be repeated per engine and drift apart.
- **A `bin/` wrapper for bpmn-cli.** A plugin `bin/` directory comes after the user's PATH, so an own installation would win. But claude.ai and Cowork do not install plugins with a `bin/` directory.
- **A version of its own for the plugin.** Nothing would tie a skill to the library version it describes, and the version was not bumped for months while the skills changed.
