# 6. Generated API shape: `FlowNodes`, shared definition files and naming

## Context

The generated code is what users compile against, so its shape is the product. It has to answer three kinds of question: what is this element (its id, its job type, its timer, its variables), what comes after it, and which identifiers does the engine resolve across process boundaries. Its names are derived from BPMN ids, which are free text and may be the modeler's defaults.

## Decision

**A Process API is one flat tree.** It holds the process id, the engine and one holder, `FlowNodes`, with one node per BPMN element whatever its sub-process depth. A node carries its own data as typed facets backed by interfaces of the runtime library ([record 8](008-runtime-library-and-process-paths.md)); a sub-process node additionally points at the start elements of its interior.

**`Next` is the only navigation holder.** A node's `Next` lists its successors, each named after the element it leads to. An entry is a `Successor` that says how the element is reached: by sequence flows (with their ids, conditions and default marker), as an attached boundary event, or as the handler associated with a compensation boundary event. Flow ids and flow labels are not used for names.

**Shared definitions are generated once per run.** Job types, messages, signals, errors, escalations and process-variable names each get one top-level type next to the Process APIs, deduplicated by value. Nodes refer to these constants instead of repeating the value.

**Variable direction is a type.** A node's variables are typed as input, output or both, and each wraps the name from the shared `ProcessVariables`.

**Names follow the target language, values are verbatim.** A flow node is a type named after its element id. A shared constant is named after its semantic identifier (the job type, the message name) in upper snake case, without a leading prefix that repeats its holder. Errors and escalations are named after their name only; the code is part of the value. Values that have to appear in annotations stay compile-time string constants; everything else is a typed wrapper, and a node additionally offers its element id as a plain constant.

**Colliding and reserved names fail generation.** Nothing is renamed automatically ([record 10](010-validation.md)).

The API itself is documented in [Generated API](/guide/generated-api); the expected-output fixtures are in [`src/test/resources/api`](https://github.com/Miragon/bpmn-to-code/tree/main/bpmn-to-code-core/src/test/resources/api).

## Consequences

- Flat addressing requires element ids that are unique across the whole model, including sub-processes.
- A job type used by five tasks, in one process or several, is one constant; one worker annotation serves all of them.
- A Process API is not self-contained: it needs the shared files of its run, which is why a run needs a package of its own ([record 5](005-one-api-per-file-deterministic-generation.md)).
- Two errors with the same name and different codes cannot both be generated. The model has to rename one.
- A model an engine accepts can be rejected by the generator until ids are renamed.
- Renaming an element in the modeler is a compile error in every place that used it, which is the point.

## Rejected alternatives

- **A second holder for sequence flows** (`outgoingFlows` with one `to<Element>` entry per flow, next to a `Next` that returned plain elements, and a path step `via` to walk a chosen flow). This was the first 6.0 design and was replaced before the release: both holders described the same successors, so the API said everything twice (the bike-leasing Kotlin API shrank by a quarter when they were joined), and several flows to the same element needed a second type next to the single flow.
- **A top-level `Flows` block with one member per sequence flow.** It needs names for flow ids, which are often the modeler's defaults, a collision rule and a reserved name of its own, in three languages. Asserting that a flow was taken is already covered by the process path.
- **Name successors after the flow id or the flow label.** Flow ids are unreadable with default ids. Labels need a sanitiser, break when a flow is relabelled and are non-local: a second `No` renames the first.
- **Keep per-kind sections inside each Process API.** Two processes served by one worker got two independent constants for one value, and nothing kept them consistent.
- **Name errors and escalations `NAME_CODE`**, or add the code only when names collide. The first embeds the code in every constant name. The second makes a constant's name depend on which other models are part of the run. Failing on the collision keeps names local.
- **Disambiguate colliding names automatically** (keep the first, or add a suffix). Keeping the first is how elements used to be dropped silently, and a suffix depends on the order and the set of files, so it is not stable.
