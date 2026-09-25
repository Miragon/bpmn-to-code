# ADR 021: Shared Definition APIs

## Status
Accepted — amends [ADR 003](003-generated-api-structure.md)

## Context

[ADR 003](003-generated-api-structure.md) nests every section inside the Process API of one process. That
fits what belongs to a single process — element ids, timers, variables, call activities, the flow — but not
the identifiers the engine resolves across process boundaries: job types, message names, signal names,
errors and escalations. Two processes served by the same worker or subscribing to the same message got two
independent constants (`AProcessApi.ServiceTasks.X`, `BProcessApi.ServiceTasks.X`) for one value, and
nothing checked that they stayed consistent.

Errors had a second problem: constants were named after the error name alone, so two errors with the same
name and different codes — different errors to the engine, which matches by code — collapsed into one
constant and one of them was silently lost.

## Decision

- **Shared definitions are generated once per run.** `ServiceTasks`, `Messages`, `Signals`, `Errors` and
  `Escalations` each become a top-level type in their own file, in the configured `packagePath`, next to the
  Process APIs. A file is only generated when at least one process has a matching element.
- **Deduplicated by value.** A value used by several processes yields one constant. Root elements are
  collected from all merged models, so the first name in sorted order wins when several map to the same
  value.
- **The Process API keeps what is process-bound**: `PROCESS_ID`, `PROCESS_ENGINE`, `Elements`,
  `CallActivities`, `Timers`, `Variables`, `Flow` / `Variants`.
- **Errors and escalations are named `NAME_CODE`** (`ERROR_INVALID_MAIL_500`); without a code, the name
  alone.
- **Collisions are checked across all processes.** Two different values that normalize to the same
  constant name — in one process or in two — fail generation. `SharedDefinitionCollisionRule` is a
  mandatory cross-model rule; `CollisionDetectionRule` keeps checking the process-local sections.
- **No flag.** This is the layout of 6.0.0; the old nesting is not kept as an option.

## Consequences

- A Process API is no longer self-contained: it belongs together with the shared files of the same run.
- The shared files are named after their kind, not after a process. Two generation runs writing into the
  same `packagePath` overwrite each other's shared files, so each run needs its own package.
- The generic type names (`ServiceTasks`, `Errors`, …) can clash with user types in the same package.
  They are package-scoped, so moving the generated code to its own package resolves that.
- Generated files are not deleted by the generator. A shared file that is no longer produced — e.g. after
  removing the last signal — stays in the output folder until it is cleaned, as a renamed process's API
  already did before.
- `NAME_CODE` is redundant for textual codes that repeat the name (`INVALID_MAIL` / `INVALID_MAIL`).
- Breaking for every 5.x user of the nested registries.
