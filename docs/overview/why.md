# Why bpmn-to-code

A BPMN model and the code that runs it share a lot of names: job types, messages, signals, errors, variables, element ids. Without a generator, those names live twice. Once in the model, once as strings in your workers, process adapters and tests:

```kotlin
@JobWorker(type = "miravelo.sendContract")   // copied from the modeler
fun sendContract() { /* ... */ }

client.newPublishMessageCommand()
  .messageName("miravelo.contractSigned")    // breaks silently when renamed
  .correlationKey(applicationId)
  .send()
```

Nothing connects the two copies. Rename a job type in the modeler and the build stays green; you find out at runtime, when a worker stops picking up jobs or a message no longer correlates.

## The model becomes the source of the names

bpmn-to-code reads your BPMN files on every build and generates one typed [Process API](/guide/generated-api) per process. Your code refers to the model instead of repeating it:

```kotlin
@JobWorker(type = ServiceTasks.MIRAVELO_SEND_CONTRACT)
fun sendContract() { /* ... */ }

client.newPublishMessageCommand()
  .messageName(Messages.MIRAVELO_CONTRACT_SIGNED.value)
  .correlationKey(applicationId)
  .send()
```

A renamed or deleted element is now a compile error in the place that uses it.

## One model, three checks

The same generated API carries more than constants, so the model is checked at three points:

| When | What | How |
|---|---|---|
| While you write code | Every element, its job type, message, timer, variables and successors are typed | [Generated API](/guide/generated-api) |
| In your tests | A process test walks a route the model contains, or it does not compile | [ProcessPath](/guide/process-path) |
| In the build | Rules catch missing implementations, unnamed messages and your own conventions | [Validation](/validate/) |

## Generated, not prompted

A coding agent can read a BPMN file and write constants, too. The difference is what happens on the next change. bpmn-to-code maps the XML to code by fixed rules: the same model gives byte-identical output on every machine and in every CI run, offline, without an API key. It cannot invent an id or drop a task, and a model change shows up as a clean diff.

That is also what makes agents useful here. The [Agent Skills](/skills/) set up the plugin, write workers and process tests and turn your BPMN style guide into rules, and they do it against the generated API instead of against XML. When the agent gets a name wrong, the compiler says so.

## Get started

Set up the [Gradle plugin](/getting-started/gradle) or the [Maven plugin](/getting-started/maven), use the [command line](https://github.com/Miragon/bpmn-to-code/tree/main/bpmn-to-code-cli), the [.NET tool](/getting-started/dotnet) or the [npm package](/getting-started/typescript), or try the [web app](/web/) without installing anything. All of them run the same generator and produce Kotlin, Java, C# (experimental) or TypeScript (experimental).
