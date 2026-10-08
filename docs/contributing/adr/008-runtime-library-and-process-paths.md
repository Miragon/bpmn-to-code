# 8. Runtime library and process paths

## Context

Generated code needs support types: wrappers for ids and names, the interfaces a node's facets are typed with, the successor types of the navigation. A build usually has several modules with generated APIs and common code that takes a `ProcessId` or a `FlowNode` as a parameter, which only works if all of them mean the same class. The typed navigation also invites a second use: describing the route a process instance took, so that a process test breaks at compile time when the model changes.

## Decision

**Generated JVM code depends on a published, hand-written library.** `io.miragon:bpmn-to-code-runtime` holds the support types. It has no dependencies. The generator emits references to it instead of emitting the types per module. The Gradle plugin adds it in its own version; Maven users declare it ([record 1](001-product-surfaces-and-distribution.md)). C# is the deliberate exception and inlines the types per file ([record 7](007-output-languages.md)).

**Generator and runtime are released together and used in the same version.** Generated code may reference runtime types that an older runtime does not have. No compatibility across versions is promised in either direction.

**Identifier wrappers are `data class`, not `value class`.** A Kotlin function with a value-class parameter is compiled to a mangled name that contains a `-`, which Java cannot call. A build that mixes Kotlin and Java modules would lose exactly the shared signatures the library exists for. A data class is an ordinary JVM class; the cost is one small allocation per wrapper.

**Nodes are equal by element id.** Two generated nodes with the same id are equal, also when they come from two files that share an element.

**`ProcessPath` is a compile-checked walk over `FlowNodes`.** A path starts at a node and advances through the node's `Next`, so only a real successor compiles. Entering and walking a sub-process is checked the same way. Leaving an activity through a boundary event and recording a compensation check the picked successor but let the caller name the activity. One step, `jumpTo`, is unchecked and needs an explicit opt-in to `@RiskyNavigation`.

**One engine, two call shapes.** Kotlin uses extension functions on `ProcessPath`. `PathWalk` wraps the same engine in instance methods for Java and has a separate terminal type, because an end event has no successors to continue with.

**A path yields string arrays.** The ids of the recorded nodes and of the sequence flows walked are arrays, so they spread into the string-vararg assertions of the engines' test libraries.

**A compensation boundary event is not recorded by default.** Throwing a compensation records the handler and stays on the throwing event. The boundary event in between is left out, because only Zeebe reports it as a passed element and Camunda 7 and Operaton do not; a flag includes it.

Usage is documented in [Process paths](/guide/process-path).

## Consequences

- One `ProcessId` class exists on the classpath of all modules, and shared code compiles against it from Kotlin and Java.
- The runtime's public types are a contract ([record 11](011-compatibility-policy.md)). Renaming one breaks every user's generated code at once.
- Users must have the runtime on their classpath and, on Maven, keep its version in step with the plugin by hand.
- A path asserts order only within one sequential branch. Parallel branches are walked separately and combined into an unordered set.
- Path semantics follow what engines report, so a difference between engines ends up as a default and a flag here.
- The path API exists for JVM languages only.

## Rejected alternatives

- **Generate the support types into every module.** Each module would get its own `ProcessId`, and code shared between modules could not be typed.
- **`@JvmInline value class` wrappers.** No allocation, but unreachable from Java, as described above.
- **Record the compensation boundary event always.** Paths written for Camunda 7 and Operaton would then contain an element the engine never reports.