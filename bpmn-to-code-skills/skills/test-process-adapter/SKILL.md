---
name: test-process-adapter
description: "Writes the unit test for one process adapter method and asserts the engine command: process, message or signal name, correlation and variables. Use when testing a process adapter or after create-process-adapter."
argument-hint: "<adapter class>[#method]"
allowed-tools: Read, Write, Edit, Glob, Grep, Bash(./gradlew *), Bash(mvn *), Bash(./mvnw *)
---

# Skill: test-process-adapter

Test one outbound call in isolation. Mock or stub the engine client and run no engine. The engine-specific test shape lives in `resources/`.

## IMPORTANT

- Expect the **generated names** (`PROCESS_ID`, `Messages.X.value`, `Signals.X.value`, `FlowNodes.<Node>.Variables.X.value`), never string literals.
- **Assert the whole command**, not just that a method was called:
  - process or message name
  - correlation key or business key
  - every variable
- **Follow the project's existing test conventions first.** Fall back to the resource:
  - **Kotlin:** backtick names, `underTest`, MockK, `confirmVerified`
  - **Java:** `@DisplayName`, Mockito, `verifyNoMoreInteractions`

## Step 1: Find the method

Resolve `$ARGUMENTS` to the adapter and the method under test. If no method is given, test every public method that has no test yet, one test each.

## Step 2: Detect the stack

Pick the style from the adapter's dependencies:

| Adapter uses | Style |
|---|---|
| `StartProcessApi`, `CorrelationApi`, `SignalApi`, `UserTaskCompletionApi` | `process-engine-api` |
| a generated engine REST client (`MessageApi`, `TaskApi`, …) | `c7-external-task` |
| `RuntimeService` / `TaskService` | `c7-delegate` |
| `CamundaClient` / `ZeebeClient` | `zeebe` |

Then read `resources/<style>-<kotlin|java>.md`, for example [resources/zeebe-kotlin.md](resources/zeebe-kotlin.md).

## Step 3: Cover these cases

1. **The command:** the expected engine command with the exact name, correlation and variables.
2. **Lookups:** for a user task completion, the task is found by its generated element id and the domain key. Add a test for "no open task" if the adapter handles it.
3. **Failures:** an engine failure reaches the caller, as an exception the adapter documents. It is not swallowed.

## Step 4: Run it

Run only this test: `./gradlew test --tests '<TestClass>'` or `./mvnw test -Dtest=<TestClass>`.
