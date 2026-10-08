# 3. BPMN reading stack, engine dialects and variable extraction

## Context

Zeebe, Camunda 7 and Operaton models are standard BPMN 2.0 plus extensions in an engine-specific XML namespace. The structure of a process is the same for all three; job types, I/O mappings, multi-instance bindings and call-activity mappings are not. The files are also untrusted input, because the web application parses uploads. And a process uses more variables than it declares: workers set them, expressions read them.

## Decision

**One parser library for every engine.** All models, including Zeebe and Operaton ones, are parsed with `camunda-bpmn-model`. It provides the typed BPMN element tree; extensions of other namespaces are read from it as plain XML.

**Hardened, single-pass parsing.** A file that contains a DOCTYPE declaration is rejected before it is parsed, and the DOM factory has external entities switched off. `BpmnDocumentParser` extends Camunda's own parser to build the DOM without validation and then validate it once against the BPMN schema the library has already compiled, instead of loading the schema and validating twice per file.

**One reader, one dialect per namespace.** `ProcessModelReader` reads the standard part of a file. Everything that lives in an engine's namespace is normalised by an `EngineDialect`, and a registry in `ExtractBpmnAdapter` maps each `ProcessEngine` to its dialect. Camunda 7 and Operaton share one dialect class that differs only in the namespace it is given. Adding an engine means a dialect, a registry entry and a detector entry, all in the outbound adapter.

**The engine is chosen by the user, never guessed.** `processEngine` is a required setting. The reader additionally notes which engine namespace a file carries, and the `engine-mismatch` rule reports an error when that differs from the selection and a warning when no engine namespace is found. There is no fallback across namespaces: an Operaton run does not read `camunda:` attributes.

**CIB seven has no engine value of its own.** Its models use the Camunda 7 namespace and are generated with `CAMUNDA_7`.

**Variables are extracted from declarations only, and every one has a direction.** The generator emits a variable when the model declares it in a construct that states whether it flows in or out: I/O mappings and multi-instance bindings on all engines, and on Camunda 7 and Operaton also call-activity mappings and the extension properties `additionalInputVariables` and `additionalOutputVariables`. Expressions are not analysed. Where a declaration's value is a bare variable reference, only the expression wrapper (`${…}`, `#{…}`, a leading `=`) is removed.

The per-engine matrix is on the [Engines](/engines/) page, the modelling side in [Modeling](/guide/modeling).

## Consequences

- The generated API states what the model declares, not what a heuristic found. A variable nobody declared is missing, and teams have to declare it; the two extension properties exist for elements without an I/O mapping.
- A collection expression such as `${order.items}` yields a variable named `order.items`, because the wrapper is stripped but the expression is not understood.
- The project depends on a Camunda 7 library, and `BpmnDocumentParser` on classes of its `impl` package. A library upgrade can break the parser, and the dependency has to be revisited when the library stops being maintained.
- Because the two Camunda-style engines share a dialect, a feature added for one works for the other without code. If Operaton's vocabulary diverges, it needs a dialect of its own.
- A model with `camunda:` attributes that should run on Operaton has to be generated with `CAMUNDA_7`.
- `engine-mismatch` is an ordinary rule: it can be disabled in the validation task, and it is not part of the testing library's default set ([record 10](010-validation.md)).

## Rejected alternatives

- **One extractor class per engine.** Each would repeat the walk over containment, sequence flows, events and boundary attachments, which is identical for every engine.
- **Read both `camunda:` and `operaton:` in an Operaton run.** It would hide a model that targets the wrong engine and make the result depend on which of two conflicting attributes wins.
- **Detect the engine from the file.** Detection is best effort: a plain BPMN file carries no engine namespace. It is good enough to warn, not to decide.
- **Mine variables from JUEL and FEEL expressions.** It needs parsers for two expression languages, yields names that may not exist in the process context, and hides models that declare too little.
- **An undirected kind of variable.** `additionalVariables` was the only source without a direction. Instead of carrying a third direction through every consumer for it, the property was replaced by an input and an output one; it is not read as an alias, because a silent reinterpretation is worse than an explicit rename.
