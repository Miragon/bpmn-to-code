# Why bpmn-to-code

You have just modeled a process. Tasks, messages, signals, timers — laid out in Camunda Modeler. Now you need to implement it. You open your IDE and start wiring the code to the process model.

This is what that looks like:

```kotlin
// Scattered across your codebase
@JobWorker(type = "miravelo.sendContract")   // copy-pasted from the modeler
fun sendContract() { /* ... */ }

client.newPublishMessageCommand()
  .messageName("miravelo.contractSigned")    // hope nobody renames this
  .correlationKey(applicationId)
  .send()
```

You copy these strings from the modeler. If someone renames a job type or a message, nothing breaks at compile time. You find out at runtime — when a worker stops picking up jobs or a message correlation silently fails.

bpmn-to-code reads your BPMN files and generates the constants from them, on every build:

```kotlin
@JobWorker(type = ServiceTasks.MIRAVELO_SEND_CONTRACT)
fun sendContract() { /* ... */ }

client.newPublishMessageCommand()
  .messageName(Messages.MIRAVELO_CONTRACT_SIGNED.value)
  .correlationKey(applicationId)
  .send()
```

Typos become compiler errors. Renamed elements break the build before they break production.

The same model feeds three more things:

- [Generated API](/guide/generated-api) — every element as a typed node with its job type, message, timer, variables and sequence flows.
- [Validation](/validate/) — built-in rules during generation, a build task, and a testing library for your own architecture rules.
- [JSON export](/surface/json) — the process structure without the diagram, for AI agents, pull request diffs and CI.

## Does AI Make This Obsolete?

My name is Marco. I built bpmn-to-code to solve exactly this. If you're interested in the whole story, you can find it [here](https://medium.com/miragon/simplifying-process-automation-with-bpmn-to-code-from-bpmn-models-to-process-apis-216adafeb0ac). Lately, I have been asking myself whether it was the right call — or whether AI has already made it obsolete.

AI coding assistants are getting better every month. They read XML, understand process structures, and generate code. You can paste a BPMN file into Claude, describe what you want, and get typed constants back.

You could even build an AI Skill that replaces the plugin entirely — scan the project for BPMN files, generate all the constants, write the output to disk. No build plugin, no configuration, no dependency.

And honestly? I can't fully deny that. So I figured I'd make it official. I pointed bpmn-to-code at [Death by Clawd](https://deathbyclawd.com?url=bpmn-to-code.miragon.io) — a tool that (even if not dead-serious 😄) scores how easily AI can replace a software product:

<img src="/death-by-clawd-score.png" alt="Death by Clawd score for bpmn-to-code: 91 out of 100" style="max-width: 420px; border-radius: 8px;" />

And the verdict was clear: 91 out of 100. Already dead. Cause of death — *Prompt engineering presented as a product.*

My defense? None. Guilty as charged — unless you ever need to do this more than once.

## Why It Still Makes Sense

Every time your BPMN model changes, you rebuild — and the Process API updates automatically. Your IDE knows every element by name. Rename a task in the modeler, and the compiler tells you exactly what broke. No string search, no grepping through the codebase, no silent failures at runtime.

And when generation is wired into your build, it runs the same way everywhere — on every machine, every branch, every CI pipeline. That shifts the question from "can AI produce this output?" to "can I trust this output unconditionally, on every run, with no external calls?"

This is where bpmn-to-code shines:

- **Deterministic output** — Same BPMN in, same code out. Every run, every machine, every time. An LLM can vary across model versions, reorder fields, or introduce formatting drift. Diffs stay clean only when output is byte-identical.
- **No hallucination risk** — The plugin maps XML to code via deterministic rules. It cannot invent an element ID or silently drop a service task. Build pipelines need guarantees, not probabilities.
- **No external dependencies** — No network calls, no API keys, no rate limits, no model deprecations. Works offline. Never flakes because a provider had an outage.

| Concern | Build toolkit | AI Skill / LLM |
|---|---|---|
| **Output stability** | Byte-identical | May vary across model versions |
| **CI/CD** | Offline, no API dependency | Requires network + API key |
| **Hallucination risk** | None | Possible invented IDs or drift |

The [JSON export](/surface/json) is where these two worlds meet: bpmn-to-code produces the reliable, deterministic process context that makes AI assistance trustworthy. Use the toolkit for generation and validation; use AI to work with the output.

## What I Suggest

Use the toolkit for deterministic generation and validation. Use AI for everything around it:

- **Setting up** the plugin in a new project — detecting BPMN files, configuring the build, choosing the right engine.
- **Migrating** existing codebases from hardcoded strings to the generated type-safe API.
- **Scaffolding** worker implementations, REST controllers, and test boilerplate from the generated Process API.
- **Understanding** a process — feed the JSON export to your coding assistant and ask questions about it.

Thus, bpmn-to-code ships with [AI Skills](/skills/) that automate these workflows. They are optional — the build plugins work standalone.

## Get Started

Set up the [Gradle plugin](/getting-started/gradle) or the [Maven plugin](/getting-started/maven), or try the [web app](/web/) without installing anything. All three run the same generator and produce Kotlin, Java or C# (experimental).
