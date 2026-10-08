# 4. BPMN-aligned domain model

## Context

The domain model is what the readers produce and what validation, code generation and the JSON export consume. BPMN allows different things on different elements: a multi-instance marker on an activity but not on a gateway, a `calledElement` only on a call activity, several event definitions on one event. A model that offers every field on every node lets invalid combinations be built and moves the check to runtime. BPMN also nests: a sub-process owns flow nodes and sequence flows of its own, and a model that only records a parent id cannot say which scope a sequence flow belongs to.

## Decision

**Flow nodes are a sealed hierarchy that mirrors the BPMN class tree.** `FlowNodeDefinition` has the subtypes `Gateway`, `Event` and `Activity`, the latter with `Task`, `SubProcess` and `CallActivity`, plus `Unknown` for elements the reader does not classify. Each facet lives on the narrowest subtype BPMN permits it on.

**Containment is structure.** A `ProcessModel` holds the flow nodes and sequence flows of the root scope; a sub-process holds its own. The flat list of all nodes, the parent of a node and node-to-node adjacency are derived by `ProcessGraph` and never stored.

**Relations follow the standard.** A node's `incoming` and `outgoing` are sequence-flow ids. An event carries a list of event definitions. Messages, signals, errors and escalations are root elements in one registry (`RootElements`), keyed by their own id and referenced from nodes by `…Ref`.

**Engine data has two forms.** What the generator understands is normalised into typed fields by the dialect ([record 3](003-bpmn-reading-and-engine-dialects.md)). Everything else in an engine namespace is kept verbatim as `EngineExtension`s and raw engine attributes. A fact is in one of the two, never in both.

**One model type per file.** A `ProcessModel` is the content of one BPMN file ([record 5](005-one-api-per-file-deterministic-generation.md)). Service tasks, timers, call activities and variables are views computed from the node tree.

The types and their KDoc are in [`domain/shared`](https://github.com/Miragon/bpmn-to-code/tree/main/bpmn-to-code-core/src/main/kotlin/io/miragon/bpmn/domain/shared).

## Consequences

- An invalid combination cannot be constructed, so neither rules nor builders have to defend against it.
- Code that reads a node matches on the subtype instead of reading nullable fields. That is more ceremony for a simple traversal, which the flat view softens.
- Tree and flat view are two shapes of the same data, and a reader has to know which one a consumer needs: the JSON export needs the tree, validation and code generation mostly the flat view.
- A new engine feature that nobody normalises still reaches the JSON export through the raw extensions, without a model change.
- The domain is the model that custom validation rules are written against ([record 1](001-product-surfaces-and-distribution.md)), so its shape is a compatibility concern ([record 11](011-compatibility-policy.md)).
- Two known impurities remain. The definition types implement `VariableMapping`, which answers with the constant name the generated code will use, so a code-generation concern sits in the domain. And an event definition carries a copy of the name of the root element it references, next to the reference.

## Rejected alternatives

- **Nullable `multiInstance` and `ioMapping` fields on one flat node type.** The smallest model, but it makes a multi-instance gateway constructible and pushes validity to runtime.
- **One node type with a one-of for its properties, plus an activity variant.** I/O mappings are legal on events as well, which an activity grouping cannot express, and a one-of cannot hold a task that has both an implementation and an I/O mapping, or an event with two triggers.
- **Nest only in the JSON adapter and keep the domain flat.** The domain could still not answer which scope a sequence flow belongs to, and every validation rule that cares about scopes would rebuild the tree itself.
