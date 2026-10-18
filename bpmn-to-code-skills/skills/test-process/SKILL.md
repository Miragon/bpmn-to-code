---
name: test-process
description: "Writes one process test that runs a BPMN scenario on a test engine and asserts the walked path as a typed ProcessPath. Use when testing a process path, a gateway branch, a boundary event or a timer."
argument-hint: "<process id> <scenario, e.g. 'happy path' or 'bike unavailable'>"
allowed-tools: Read, Write, Edit, Glob, Grep, Bash(./gradlew *), Bash(mvn *), Bash(./mvnw *), Bash(bpmn --version), Bash(bpmn show *), Bash(bpmn find *), Bash(npx -y @miragon/bpmn-cli@0.7 *)
---

# Skill: test-process

Test one scenario of a process end to end on a test engine:
- the BPMN model is real
- the workers and the process adapter are real
- the use cases are mocked

Assert the walked path with `ProcessPath` from `bpmn-to-code-runtime`. The engine-specific harness lives in `resources/`.

## IMPORTANT

- **Build the expected path from `FlowNodes`:** `ProcessPath` in Kotlin, `PathWalk` in Java. Never list element ids as strings. If the model changes, the test must stop compiling.
- **One scenario per test method.** A parallel gateway gets one path per branch, asserted unordered (`nodesOf`).
- **Drive the instance only through the real entry points:**
  - start and messages through the process adapter or the engine API the resource names
  - service tasks through the real workers, with mocked use cases deciding the branch
  - exception: with a remote engine, the in-memory test engine has no REST API for workers and adapter. Its resource drives the engine directly, with the same generated names.
- **Never edit generated files.**

**bpmn-cli:** run `bpmn --version` once. If it fails, use `npx -y @miragon/bpmn-cli@0.7` wherever this skill says `bpmn`. Without Node, read the BPMN XML instead.

## Step 1: Pick the path

1. Read the model in flow order: `bpmn show <file>`. For one branch, use `bpmn show <file> --around <id>`. Without `bpmn`, read the BPMN XML.
2. Turn `$ARGUMENTS` into a concrete walk:
   - the start event
   - each gateway decision and which mocked use case result causes it
   - each wait state: message, user task, timer
   - the end event
3. Show the walk to the user before writing code if the scenario was vague.

## Step 2: Detect the stack

Pick the style by the first match in the build files:

| Dependency | Style |
|---|---|
| `dev.bpm-crafters.process-engine-api` | `process-engine-api` |
| `…external-task-client…` (Camunda 7, CIB seven, Operaton) | `c7-external-task` |
| `…bpm-spring-boot-starter…` of Camunda 7, CIB seven, Operaton or Fluxnova | `c7-delegate` |
| `io.camunda:camunda-spring-boot-starter` | `zeebe` |

Then read `resources/<style>-<kotlin|java>.md`, for example [resources/zeebe-kotlin.md](resources/zeebe-kotlin.md). If the project already has process tests, reuse their base class and helpers instead of the resource's.

## Step 3: Write the path

```kotlin
val path = ProcessPath.from(FlowNodes.StartEventLeasingRequestReceived)
    .then { it.serviceTaskValidateApplication }
    .then { it.businessRuleTaskCheckCreditRating }
    .then { it.gatewayIsSolvent }
```

| Situation | Step |
|---|---|
| a subprocess | `onto { it.sub }.inside { enter { it.start }.then { … } }` |
| leaving through a boundary event | `interruptedBy(FlowNodes.Host) { it.boundary }` |
| compensation | `throwingCompensation(FlowNodes.Boundary) { it.handler }` |

Java uses `PathWalk.from(FlowNodes.x()).then(n -> n.y())…end(n -> n.z()).getIds()`. Full step table: https://miragon.github.io/bpmn-to-code/guide/process-path.

## Step 4: Write the test

Follow the resource for:
- setup
- starting the instance
- driving wait states (jobs, external tasks, messages, timers)
- the assertion that takes `path.ids`

## Step 5: Run it

Run only this test. A failure usually means the scenario's mocked results do not lead down the walked branch. Fix the setup, not the path, unless the model really differs from the expectation.
