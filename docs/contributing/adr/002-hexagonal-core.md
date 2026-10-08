# 2. Hexagonal core with a plugin facade, enforced by Konsist

## Context

One code base serves three process engines, three output languages and four callers (Gradle, Maven, web, testing library). Engines and languages change independently of each other and of the rules that decide what a valid process model is. The callers must not be able to reach into the generator's internals, because every class they touch becomes hard to change ([record 1](001-product-surfaces-and-distribution.md)).

## Decision

`bpmn-to-code-core` is a hexagon with three layers:

- **Domain** (`domain`): the process model, the validation rules and the naming logic. It imports nothing but the Kotlin and Java standard libraries, the kotlin-logging facade and itself.
- **Application** (`application`): one inbound port per use case, the outbound ports, and one service per use case.
- **Adapters** (`adapter`): outbound adapters for BPMN reading, code generation, JSON and the file system; inbound adapters named `*Plugin`.

**The inbound `*Plugin` classes are the facade.** Other modules may import core's `domain` and `adapter.inbound` packages and nothing else.

**There is no dependency injection framework.** A service names its outbound adapters as default values of its constructor parameters, and a plugin does the same with its service. The application layer is therefore allowed to import outbound adapters. What stays strict is the type: a constructor parameter is always typed as the port, never as the adapter or service behind it, so tests and callers can pass another implementation.

**Each use case exists for the file system and in memory.** Generating code and generating JSON each have a port, a service and a plugin that read and write files, and a second set that takes BPMN content and returns the generated text. Services do not call each other, so the two pipelines are written out twice rather than shared.

**The rules are tests.** The unpublished module `bpmn-to-code-architecture-tests` checks with Konsist: the layer dependencies above, the domain's import allow-list, that ports are interfaces, that a service implements exactly one inbound port and a plugin at most one, the constructor typing rule, the naming of each layer's classes, and the import restriction for the Gradle, Maven and web modules.

The module map is in [Architecture](/contributing/architecture); the other quality gates are in the [contributing guide](/contributing/).

## Consequences

- A new engine or language is an outbound adapter; the domain and the callers do not change.
- The in-memory path is what makes the web application stateless and lets tests run the real pipeline without touching disk.
- Each use case costs a port, a service and a plugin, and a change to the generation pipeline has to be made in the file-system and the in-memory service.
- The layer rule alone would let a service use an adapter directly. Only the constructor typing rule and review keep the ports meaningful.
- The hexagon is a convention of core. The plugin modules keep their classes flat under `adapter`, the web module has its own `routes`, `service` and `model` packages.
- Konsist reads Kotlin sources. The Maven Mojos are written in Java, so the import restriction does not actually check them, and the testing library is not in the checked list.
- Two domain rules know engine vocabulary: `engine-mismatch` names the engines, and `missing-service-task-implementation` tells the user which engine attribute to set. This is accepted for two rules; a third one is the trigger to move the wording behind a port.

## Rejected alternatives

- **A DI framework.** Core's classes are copied into the plugin jars and its dependencies land on the user's build classpath ([record 1](001-product-surfaces-and-distribution.md)), so a container would be one more library there, for a handful of services with at most four collaborators each. Constructor defaults wire them in plain Kotlin.
- **A plain layered design without ports.** Engine parsing, code generation and the file system would be reachable from the domain, and the in-memory path would need conditionals inside the services instead of a second set of adapters.
