# Architecture decisions

These records describe the architecture of bpmn-to-code as it is today, as a set of decisions: what was decided, what follows from it, and which alternatives were turned down and why. They are not a history. When a decision changes, its record is edited in place so that it stays true; what it said before is in git, and what changed for users is in the [changelog](/changelog/).

Change a record in the same pull request that changes what it describes. Write a new one when a change introduces a decision that a contributor would otherwise have to rediscover: a new product surface, a new public contract, a constraint that is not visible from the code, or an alternative that was seriously considered and rejected. Conventions, commands and thresholds belong in the [contributing guide](/contributing/) and in [Architecture](/contributing/architecture), not here.

Every record has the same sections: Context, Decision, Consequences, Rejected alternatives.

1. [Product surfaces, distribution and uniform entry points](001-product-surfaces-and-distribution.md): what is published, why core is not, and why every entry point behaves the same.
2. [Hexagonal core with a plugin facade](002-hexagonal-core.md): layers, wiring without a DI framework, and the rules Konsist enforces.
3. [BPMN reading stack, engine dialects and variable extraction](003-bpmn-reading-and-engine-dialects.md): one parser for all engines, a dialect per namespace, explicit and directional variables.
4. [BPMN-aligned domain model](004-bpmn-aligned-domain-model.md): a sealed flow-node hierarchy with structural containment.
5. [One API per file, deterministic whole-run generation, generated-file ownership](005-one-api-per-file-deterministic-generation.md): naming by `variantName`, byte-identical output, and which files the generator may delete.
6. [Generated API shape](006-generated-api-shape.md): `FlowNodes`, `Next`, shared definition files and naming.
7. [Output languages](007-output-languages.md): the language-neutral `FlowGraph`, poet builders for Kotlin and Java, a text writer for C#.
8. [Runtime library and process paths](008-runtime-library-and-process-paths.md): the published support types and the compile-checked `ProcessPath`.
9. [Process JSON contract](009-process-json-contract.md): a schema-versioned, BPMN-aligned public format.
10. [Validation](010-validation.md): the rule model, mandatory rules, and the three places validation runs.
11. [Compatibility and stability policy](011-compatibility-policy.md): the public contracts, what may break when, and the JDK floor.
12. [Web application](012-web-application.md): a stateless entry point with a static frontend, and its operating constraints.
