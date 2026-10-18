---
name: create-worker
description: "Implements one BPMN service task as a job worker, JavaDelegate or external-task handler that calls one use case, with names from the generated Process API. Use when a service task or job type needs code that handles it."
argument-hint: "<element id or job type>"
allowed-tools: Read, Write, Edit, Glob, Grep, Bash(./gradlew *), Bash(mvn *), Bash(./mvnw *), Bash(bpmn --version), Bash(bpmn show *), Bash(bpmn find *), Bash(npx -y @miragon/bpmn-cli@0.7 *)
---

# Skill: create-worker

Write one inbound adapter: the class the engine calls for one BPMN element. It translates engine data into domain types, calls one use case, and translates the result back. The engine-specific code shape lives in `resources/`.

## IMPORTANT

- **Names come from the generated API only:**
  - the job type or topic: `ServiceTasks.X`
  - variables: `FlowNodes.<Node>.Variables.X.value` in code, or `ProcessVariables.X` where an annotation needs a compile-time constant (`@Variable(name = …)`)
  - error codes: `Errors.X.code`
  
  No string literals. If a name is missing from the API, the model does not declare it. Tell the user what to declare in the model (Zeebe I/O mapping, Camunda 7 `additionalInputVariables` / `additionalOutputVariables`), regenerate, then continue.
- **One handler per element, one use case per handler.** No business logic in the handler.
- **Error mapping:** a business error that the model catches with an error boundary event becomes a BPMN error with `Errors.X.code`. Everything else is a technical failure and goes to retry or incident.
- **Never edit generated files.**

**bpmn-cli:** run `bpmn --version` once. If it fails, use `npx -y @miragon/bpmn-cli@0.7` wherever this skill says `bpmn`. Without Node, read the BPMN XML instead.

## Step 1: Find the element

1. Locate the generated API: `*ProcessApi.kt` / `*ProcessApi.java` next to `ServiceTasks`. If it is missing, run `/setup-bpmn-to-code` first. If it is older than the BPMN file, regenerate.
2. Resolve `$ARGUMENTS` (element id or job type) to its `FlowNodes.<Node>` and its `ServiceTasks` constant.
3. Collect what the handler needs:
   - **input and output variables** from `FlowNodes.<Node>.Variables`
   - **error boundary events** and their codes. Read them with `bpmn show <file> <element-id> --context`, which lists `caught by:` and the boundary events. If `bpmn` is not available, read the BPMN XML.

## Step 2: Detect the stack

Pick the style by the first match in the build files:

| Dependency | Style |
|---|---|
| `dev.bpm-crafters.process-engine-worker` / `process-engine-api` | `process-engine-api` |
| `…external-task-client…` (Camunda 7, CIB seven, Operaton) | `c7-external-task` |
| `…bpm-spring-boot-starter…` of Camunda 7, CIB seven, Operaton or Fluxnova | `c7-delegate` |
| `io.camunda:camunda-spring-boot-starter` / `spring-boot-starter-camunda-sdk` | `zeebe` |

Language: Kotlin if the existing adapters are `.kt`, else Java. Then read `resources/<style>-<kotlin|java>.md`, for example [resources/zeebe-kotlin.md](resources/zeebe-kotlin.md).

**Follow what the project already does.** Read one existing handler of the same kind (base class, package, naming, logging) and copy its shape over the resource's default where they differ.

## Step 3: Find the use case

- Find the inbound port (`application.port.inbound`, suffix `UseCase`) that does the work.
- If there is none, propose its signature with domain types, not primitives or engine types, and create only the interface after confirmation. Implementing the service is a separate task.

## Step 4: Write the handler

Follow the resource. Place it in `adapter.inbound.<engine>` (or the package existing handlers use) and name it after the element (`OrderBikeWorker`, `OrderBikeDelegate`). Wire it to the engine the way the resource shows, e.g. the delegate expression bean name or the topic subscription.

## Step 5: Verify and hand over

1. Compile: `./gradlew compileKotlin` / `compileJava`, or `./mvnw compile`.
2. Name the next step: `/test-worker <HandlerClass>`. Behaviour across several elements is `/test-process`.
