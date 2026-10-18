---
name: test-worker
description: "Writes the unit test for one job worker, JavaDelegate or external-task handler, covering inputs, outputs, BPMN errors and retries. Use when testing a worker or after create-worker."
argument-hint: "<handler class or element id>"
allowed-tools: Read, Write, Edit, Glob, Grep, Bash(./gradlew *), Bash(mvn *), Bash(./mvnw *)
---

# Skill: test-worker

Test one inbound adapter in isolation. Mock the use case and the engine objects, and run no engine. The engine-specific test shape lives in `resources/`.

## IMPORTANT

- Assert against the **generated names** (`FlowNodes.<Node>.Variables.X`, `Errors.X.code`), never against string literals. Then a model change breaks the test at compile time.
- One test class per handler, next to it in the test source tree.
- **Follow the project's existing test conventions first.** Fall back to the resource:
  - **Kotlin:** backtick names, `underTest`, MockK, `confirmVerified`
  - **Java:** `@DisplayName`, Mockito, `verifyNoMoreInteractions`

## Step 1: Find the handler

Resolve `$ARGUMENTS` to the handler class. For an element id, find the class that references its `ServiceTasks` constant. Read the handler and its use case.

## Step 2: Detect the stack

Pick the style from the handler's annotations and imports:

| Handler uses | Style |
|---|---|
| `@ProcessEngineWorker` | `process-engine-api` |
| `ExternalTaskHandler` / `@ExternalTaskSubscription` | `c7-external-task` |
| `JavaDelegate` | `c7-delegate` |
| `@JobWorker` | `zeebe` |

Then read `resources/<style>-<kotlin|java>.md`, for example [resources/c7-delegate-kotlin.md](resources/c7-delegate-kotlin.md).

## Step 3: Cover these cases

1. **Happy path:**
   - the use case receives the domain values built from the input variables
   - every output variable is written under its generated name (or returned)
   - the task completes
2. **One test per BPMN error** the handler throws: the domain exception becomes `Errors.X.code`, and nothing is completed or written.
3. **Technical failure:** an unexpected exception is not swallowed. It reaches the engine as a retry or failure, the way the resource describes for this style.
4. No further interactions with the use case or the engine objects.

Skip a case only if the handler has no such branch.

## Step 4: Run it

Run only this test: `./gradlew test --tests '<TestClass>'` or `./mvnw test -Dtest=<TestClass>`. Fix the test, not the handler, unless the test exposes a real bug. If it does, report the bug.
