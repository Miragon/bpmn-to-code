# 11. Compatibility and stability policy

## Context

Users compile against generated code, configure plugins in their build, parse the JSON export and write validation rules against the domain model. Each of these breaks when its counterpart changes. Without a written rule, every change starts the same discussion: is this breaking, and may it ship now?

## Decision

**One version for everything.** The Gradle plugin, the Maven plugin, the runtime library, the testing library and the web image carry the same version. release-please manages a single version in `gradle.properties`, and every module reads it from there. The agent skills are the exception and are versioned on their own ([record 1](001-product-surfaces-and-distribution.md)).

**The public contracts are:**

- the shape and names of the generated API ([record 6](006-generated-api-shape.md)),
- the public types of the runtime library ([record 8](008-runtime-library-and-process-paths.md)),
- the process JSON, which has a format version and schema of its own ([record 9](009-process-json-contract.md)),
- the names and parameters of the plugin tasks and goals,
- the ids of the validation rules,
- core's `domain` package, for writing custom validation rules and for nothing else.

Everything else in core, including the inbound adapters, is internal and may change in any release.

**Stable surfaces break only in a major release.** A minor release adds; a patch release fixes.

**Experimental surfaces may break in a minor release.** A surface is experimental when the documentation or the code says so. Today that is the C# output and the build-time validation task and goal. The word is "experimental" everywhere.

**Breaking changes ship without flags or compatibility layers.** A major release changes the output, and the migration guide in the [changelog](/changelog/) describes the way over. There is no option to keep the previous shape and no deprecated copy of a renamed type.

**One exception is on record.** 6.2.0 removed the `enableVariants` setting and the merged API it enabled, in a minor release ([record 5](005-one-api-per-file-deterministic-generation.md)). It is an exception, not a precedent.

**Generated code and runtime are used in the same version** ([record 8](008-runtime-library-and-process-paths.md)).

**JDK 21 is the floor.** All modules are compiled to Java 21 bytecode. A build needs JDK 21 or newer to run the plugins, and a project needs it to use the runtime library.

**The generator does not version generated APIs.** In-flight process instances of an older model are the application's concern; it keeps the constants it still needs by hand.

## Consequences

- "Is this breaking?" has an answer: does it change one of the listed contracts, and is that surface experimental?
- A change to a domain type is a breaking change for users with custom rules, even when no plugin user notices it.
- Renaming a validation rule id breaks build scripts that disable it.
- A user cannot take a fix for one artifact without moving all of them, and a release that changes only the web application still publishes every artifact.
- A project on a JDK older than 21 cannot use any part of the library.
- Because skills are versioned apart, nothing guarantees a skill matches the library version it is used with.

## Rejected alternatives

- **File-based versioning of generated APIs.** A `useVersioning` setting once suffixed the API with `V<n>` and kept a counter in a properties file. It was removed: several versions of an API coexisted and usage spread across them, so there was no single source of truth; the counter file was written during builds and left working trees dirty in CI; the branching complicated generation; and hardly anybody used it.
- **Independent versions per artifact.** Generated code may use runtime types an older runtime lacks, and the Gradle plugin adds the runtime in its own version. A version matrix would have to be documented and tested for no benefit to users.
- **Compatibility layers for renamed types.** The move of the runtime to the `io.miragon` package shipped with deprecated copies under the old package for one major version. They were removed afterwards, and no later breaking change repeated the approach.
