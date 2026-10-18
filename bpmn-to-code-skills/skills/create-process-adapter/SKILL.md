---
name: create-process-adapter
description: "Adds one call from the application to the process engine (start a process, correlate a message, send a signal, complete a user task) as an outbound port and process adapter method. Use when code has to start or continue a BPMN process."
argument-hint: "<start|message|signal|user-task> <process id, message, signal or element id>"
allowed-tools: Read, Write, Edit, Glob, Grep, Bash(./gradlew *), Bash(mvn *), Bash(./mvnw *), Bash(bpmn --version), Bash(bpmn show *), Bash(bpmn find *), Bash(npx -y @miragon/bpmn-cli@0.7 *)
---

# Skill: create-process-adapter

Write one outbound call to the engine. The application talks to a port in its own language (domain types, business verbs). The adapter turns that into the engine command. The engine-specific command shape lives in `resources/`.

## IMPORTANT

- **Names come from the generated API only:**
  - `<Process>ProcessApi.PROCESS_ID`
  - `Messages.X`
  - `Signals.X`
  - `FlowNodes.<UserTask>`
  - `FlowNodes.<Node>.Variables.X`

  No string literals. A missing name means the model does not declare it: say so and stop.
- **Ports speak domain:** parameters are domain types, and the adapter converts them to engine values.
- **Never edit generated files.**

**bpmn-cli:** run `bpmn --version` once. If it fails, use `npx -y @miragon/bpmn-cli@0.7` wherever this skill says `bpmn`. Without Node, read the BPMN XML instead.

## Step 1: Identify the call

Resolve `$ARGUMENTS` to one of these:

| Call | Generated name | Read from the model |
|---|---|---|
| start | `PROCESS_ID`, or `Messages.X` for a message start event | the start event's variables |
| message | `Messages.X` | the catching element: `bpmn show <file> <id> --context` shows its correlation key |
| signal | `Signals.X` | the catching elements (`bpmn find <file> <signal>`) |
| user task | `FlowNodes.<UserTask>` | its output variables |

Without `bpmn`, read the BPMN XML.

## Step 2: Detect the stack

Pick the style by the first match in the build files:

| Dependency | Style |
|---|---|
| `dev.bpm-crafters.process-engine-api` | `process-engine-api` |
| `…external-task-client…` or a generated engine REST client (Camunda 7, CIB seven, Operaton) | `c7-external-task` |
| `…bpm-spring-boot-starter…` of Camunda 7, CIB seven, Operaton or Fluxnova | `c7-delegate` |
| `io.camunda:camunda-spring-boot-starter` / `spring-boot-starter-camunda-sdk` | `zeebe` |

Language: Kotlin if the existing adapters are `.kt`, else Java. Then read `resources/<style>-<kotlin|java>.md`, for example [resources/process-engine-api-kotlin.md](resources/process-engine-api-kotlin.md).

## Step 3: Choose the port

Look in `application.port.outbound` first and **follow the existing pattern**:

| Project has | Do |
|---|---|
| one port per process (e.g. `LeasingProcess` with several methods) | add a method to it |
| one port per call | add a new port |
| nothing yet | **recommend one port per call**, and let the user decide |

**One port per call:**
- A small interface with one method named after the business event, e.g. `StartBikeLeasingPort.startBikeLeasing(applicationId)` or `NotifyContractSignedPort.notifyContractSigned(applicationId)`.
- The method name must be unique across the process's ports, because one adapter implements them all. Generic names such as `notify` would clash, and in Java `notify` collides with `Object.notify`.
- The application service depends only on what it uses.
- Use the suffix the project's architecture rules allow (`Port` / `Process`).

**Adapter:** one `<Process>ProcessAdapter` per process in `adapter.outbound.<engine>` implements every port of that process.

## Step 4: Write the call

Follow the resource for the call type. It covers:
- the correlation key versus the business key
- variables
- transactions
- the engine-specific pitfalls

Call the port from the application service after the domain state is saved.

## Step 5: Verify and hand over

1. Compile.
2. Name the next step: `/test-process-adapter <AdapterClass>`. A test of the process path the call triggers is `/test-process`.
