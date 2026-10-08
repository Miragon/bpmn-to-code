# 9. Process JSON contract

## Context

Some consumers want the structure of a process but cannot or should not parse BPMN XML: AI assistants that need process context, reviewers who want to read a process change as a text diff, tools written in languages the generator does not target. For them the generator emits a JSON document. Once other tools parse it, its shape is a contract, and they need to know which version they are reading and when it may change.

## Decision

**JSON is a separate, optional output.** One document per BPMN file, named after the file's API name ([record 5](005-one-api-per-file-deterministic-generation.md)), produced by its own Gradle task, Maven goal and web endpoint, and available in memory like code generation.

**It is a public contract with a published schema.** Every document names the JSON Schema it conforms to in `$schema` and its `formatVersion`. The schema is served from the documentation site and also packed into the plugin jars under `META-INF/bpmn-to-code/schema`, so a consumer can validate offline against the schema of its generator version.

**Its shape follows OMG BPMN 2.0 and the vocabulary of `bpmn-moddle`.** An element's type is its BPMN name. A scope owns the flow nodes and sequence flows it contains. A node's `incoming` and `outgoing` hold sequence-flow ids. An event has a list of event definitions. Messages, signals, errors and escalations are root elements in `definitions`, referenced by `…Ref`. A consumer who knows BPMN needs no translation table.

**Every element has three layers.** The standard BPMN core; facets normalised across engines, such as the implementation of a task; and the raw, namespaced engine extensions and attributes. A fact appears in exactly one layer: what a dialect normalises completely is left out of the raw layer ([record 4](004-bpmn-aligned-domain-model.md)).

**Additive changes raise the minor `formatVersion` within the same schema document.** A breaking change gets a new schema document under a new URL. The schema is closed, so an unknown field tells a consumer the document comes from a newer generator.

**Output is deterministic.** Within each scope, nodes are ordered depth-first from the start events, so a document reads in process order and diffs stay small.

The structure is documented in [JSON export](/surface/json); the schema is [`process-model/2.0.json`](https://miragon.github.io/bpmn-to-code/schema/process-model/2.0.json).

## Consequences

- A consumer can pin a schema and validate mechanically. `ProcessJsonSchemaTest` validates every shared model's output against the schema and additionally checks that the references resolve, which a schema cannot.
- "All nodes" needs recursion through sub-processes, and "what comes after this node" needs a join through `sequenceFlows`.
- Variables are not part of the JSON. Direction-typed variables exist in the generated code only ([record 6](006-generated-api-shape.md)).
- Root elements that no node references are emitted as well; a validation rule in the testing library reports them.
- The schema still describes a `variants` property that was emitted up to 6.1 and is no longer generated.
- Lanes, data objects, artifacts and collaborations are not part of the format. The layered shape lets them be added as a minor version.
- The schema URL is part of the contract: the documentation site has to keep serving it.

## Rejected alternatives

- **Stay flat and add a `scopeId` to nodes and flows.** The friendliest shape for `grep` and `jq`, but nesting stays a reference instead of a structure, and a `children` list next to `scopeId` brings back the redundancy.
- **Keep node-to-node adjacency next to `sequenceFlows`.** Best for "what comes after X", but it states every connection twice and still cannot attribute a condition to a branch.
- **Emit only `sequenceFlows`, without relation fields on nodes.** Fully normalised and smallest, but every consumer has to build an index before it can answer anything.
- **A convenience type such as `MESSAGE_START_EVENT` next to `eventDefinitions`.** Derived data that cannot represent an event with several triggers, so it would be wrong exactly where it matters.
