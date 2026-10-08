# 10. Validation: rule model, mandatory rules and the three places it runs

## Context

Turning BPMN ids into identifiers of a programming language is lossy: `task-ship` and `task_ship` yield the same name, and an id can yield a name the generated API uses itself. A model can also be incomplete in ways an engine only reports at deployment or runtime, such as a service task without an implementation. And teams have conventions of their own that they want to check in a test suite, the way ArchUnit checks code.

## Decision

**Rules are domain code in core.** A `ValidationRule` has an id, a severity (error or warning) and a `mandatory` flag. It is either a `SingleModelValidationRule`, which sees one process model, or a `CrossModelValidationRule`, which sees all models of a run. Cross-model rules run only when no single-model rule reported an error. All findings are collected and reported together.

**Validation is part of generation.** Every generation run, for code and for JSON, on every entry point, validates the models before it writes anything. Errors fail the run, warnings are logged. Generation takes no validation settings ([record 1](001-product-surfaces-and-distribution.md)).

**Mandatory rules cannot be disabled.** The rules without which the output would not compile or would silently lose an element are marked mandatory: missing process and element ids, colliding names within a Process API, colliding names of shared definitions, and reserved names. Core keeps them active whatever the configuration says and logs a warning when a caller tries to disable one.

**Name checks mirror where the name lives.** Flow nodes are checked per process, call-activity mappings per node, shared definitions and variable names across the whole run. Independently of the rules, files that would be generated under the same API name are rejected ([record 5](005-one-api-per-file-deterministic-generation.md)). Nothing is renamed automatically ([record 6](006-generated-api-shape.md)).

**Validation runs in three places.**

- *Generation*: the built-in rule set, no settings, non-executable processes left out before validation.
- *The build task* (`validateBpmnModels`, `validate-bpmn`): the same rule set without generating. Rules can be disabled by id and warnings can fail the build. It is experimental ([record 11](011-compatibility-policy.md)).
- *The testing library* (`bpmn-to-code-testing`): a fluent test API with AssertJ assertions. It has a default rule set of its own, further opt-in rules, and is the only place where custom rules can be added.

**The testing library is a separate artifact.** It exposes AssertJ, which must not reach the classpath of the build plugins. It carries core's classes itself ([record 1](001-product-surfaces-and-distribution.md)).

The rule table is in [Validate](/validate/), the test API in [Testing](/validate/testing) and [Custom rules](/validate/custom-rules).

## Consequences

- A model an engine accepts can be rejected by the generator until ids are renamed.
- The build plugins have no extension point for rules. A custom rule runs in tests only.
- The three places do not behave identically. The build task validates non-executable processes, generation does not. The testing library's default set contains `unreferenced-root-element` and lacks `engine-mismatch`; the built-in set of generation and the build task is the other way round. The testing library selects rules before core sees them, so there a mandatory rule can be disabled or left out.
- A custom rule is written against core's `domain` package, which makes that package public API ([record 11](011-compatibility-policy.md)).

## Open questions

- Should the build-time validate task skip non-executable processes, as generation does?
- Is it intentional that `engine-mismatch` is absent from `BpmnRules.all()`?
- Should the testing library keep being able to disable mandatory rules?

## Rejected alternatives

- **Disambiguate colliding names instead of failing.** See [record 6](006-generated-api-shape.md).
- **Put the test API into core.** AssertJ would become a dependency of the build plugins for every user.
- **A static assertion API instead of the fluent builder.** The builder mirrors ArchUnit, which the audience knows, and has a natural place for optional settings such as the rule set and the warning policy.
- **Validation phases that a rule can choose.** They existed for rules that had to run before or after models were merged. Without merging there is one order: single-model rules, then cross-model rules.
