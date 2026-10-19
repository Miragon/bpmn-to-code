---
name: setup-bpmn-to-code
description: "Sets up bpmn-to-code in an existing project with the Gradle plugin, the Maven plugin or the CLI, so the build generates a type-safe Process API from the BPMN models. Use when adding bpmn-to-code or BPMN code generation to a project."
argument-hint: "[gradle|maven|cli]"
allowed-tools: Read, Write, Edit, Glob, Grep, Bash(./gradlew *), Bash(mvn *), Bash(./mvnw *), Bash(bpmn-to-code *)
---

# Skill: setup-bpmn-to-code

Configure bpmn-to-code once per project. The build-tool specifics live in `resources/`; read only the one that matches.

## IMPORTANT

- Show a draft of every change and wait for confirmation before editing.
- Never overwrite an existing bpmn-to-code configuration without asking. If one exists, offer to fix it instead.
- Use one version for every bpmn-to-code artifact (plugin, runtime, testing).
- Never edit generated files. They are rewritten on every run.

## Step 1: Detect the stack

1. **Build tool**, by the first match in the project root (or `$ARGUMENTS`):

   | Found | Resource |
   |---|---|
   | `build.gradle.kts` or `build.gradle` | [resources/gradle.md](resources/gradle.md) |
   | `pom.xml` | [resources/maven.md](resources/maven.md) |
   | a `*.csproj` | [resources/dotnet.md](resources/dotnet.md) |
   | neither, or a `package.json` | [resources/cli.md](resources/cli.md) |

   In a multi-module build, ask which module holds the BPMN files and set it up there.

2. **BPMN files:** Glob `**/*.bpmn`, excluding `build/`, `target/` and `node_modules/`. Derive `filePattern` from their common directory.
3. **Engine**, from the namespace the BPMN files use, not from the engine name:

   | Namespace in the BPMN files | `processEngine` | Typical engines |
   |---|---|---|
   | `xmlns:zeebe` | `ZEEBE` | Camunda 8 |
   | `xmlns:camunda` | `CAMUNDA_7` | Camunda 7, CIB seven, Fluxnova, Operaton models kept in the Camunda namespace |
   | `xmlns:operaton` | `OPERATON` | Operaton |

   If files use different namespaces, one task or execution per group is needed (see the resource).
4. **Output language:** `src/main/kotlin` → `KOTLIN`, `src/main/java` only → `JAVA`, a `*.csproj` → `CSHARP` (experimental), a `package.json` with TypeScript sources → `TYPESCRIPT` (experimental).
5. **Package:** suggest `<base package>.adapter.process` (or `.process` if the project has no `adapter` package). Look up the base package from existing sources.

## Step 2: Ask what is left

Ask only for what Step 1 could not settle, with the detected value as the default:

- Package of the generated code.
- **Where the generated code goes:**
  - **committed in the source folder** (`src/main/<lang>`): visible in reviews, needs a CI drift check.
  - **in the build directory**: never committed, always fresh.
- Whether to add the **model validation test** with `bpmn-to-code-testing` (recommended).

## Step 3: Look up the version

Read `<release>` from `https://repo1.maven.org/maven2/io/miragon/bpmn-to-code-runtime/maven-metadata.xml`. All artifacts and the CLI share that version.

## Step 4: Draft, confirm, apply

Read the matching resource and build the draft from it:

1. the plugin or CLI call
2. the runtime dependency where the resource says it is needed
3. the hook into the build
4. the testing dependency and the test, if wanted

Show the draft and ask: *"Apply these changes? (yes / edit / cancel)"*. Then apply them.

## Step 5: Verify

1. Run the generation command from the resource.
2. List the generated files: `<Process>ProcessApi`, `ServiceTasks`, `Messages`, `ProcessVariables`, …
3. Run the model validation test if one was added.

If generation fails, read the reported rule violation and point to the element id. Do not disable rules to make it pass.

## Step 6: Point to the next step

- Hardcoded element ids, task types or message names in the code: `/migrate-to-bpmn-to-code-apis`.
- New engine glue:
  - `/create-worker` for a service task
  - `/create-process-adapter` for a start, message, signal or user task call
- Generated code committed: the drift check in CI, see https://miragon.github.io/bpmn-to-code/guide/verify-in-ci.
