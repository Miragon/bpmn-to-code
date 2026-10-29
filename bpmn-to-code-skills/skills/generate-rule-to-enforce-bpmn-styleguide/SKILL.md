---
name: generate-rule-to-enforce-bpmn-styleguide
disable-model-invocation: true
argument-hint: "[rule-slug]"
description: "Turns one rule of a BPMN_STYLE_GUIDE.md into a Kotlin validation rule for bpmn-to-code-testing and registers it in the model validation test. Use when a modeling convention should be enforced in the build."
allowed-tools: Read, Write, Edit, Glob, Grep, Bash(./gradlew *), Bash(mvn *), Bash(./mvnw *), Bash(javap *)
---

# Skill: generate-rule-to-enforce-bpmn-styleguide

Turn one rule of `BPMN_STYLE_GUIDE.md` into a Kotlin rule for `bpmn-to-code-testing`. The class template and what a rule can read are in [resources/rule-template.md](resources/rule-template.md).

## IMPORTANT

- **Only rules with a deterministic half:**
  - `validation: deterministic` → generate the rule.
  - `validation: hybrid` → generate the deterministic half only.
  - `validation: llm` → nothing to generate, because the rule needs judgement and not a test. Say so and stop.
- One rule per file and per call.
- Rules are Kotlin. A Java project still needs `src/test/kotlin` with the Kotlin plugin. If it has none, say so and stop.

## Step 1: Check the testing dependency

Grep the build files (root and one level below) for `bpmn-to-code-testing`. If several modules have it, ask which one hosts the rule. If none has it, show this and stop:

```kotlin
testImplementation("io.miragon:bpmn-to-code-testing:<version>")
```

```xml
<dependency>
    <groupId>io.miragon</groupId>
    <artifactId>bpmn-to-code-testing</artifactId>
    <version><version></version>
    <scope>test</scope>
</dependency>
```

Use the version of the other bpmn-to-code artifacts in the build.

## Step 2: Resolve the rule

1. Find `BPMN_STYLE_GUIDE.md` (current directory, then git root). If it is missing, say so and stop. The style guide skills of [Miragon/bpmnlint-rules](https://github.com/Miragon/bpmnlint-rules) create one.
2. Find every `<!-- rule:<slug> -->` anchor. A rule is the anchor, the next fenced `yaml` block and the prose above it.
3. Take the slug from `$ARGUMENTS`. If none is given, list the slugs with their `validation` and ask.

## Step 3: Confirm the check

Show:
- the classification
- which domain property the rule reads (see the cheat sheet)
- the regex or structural check

Wait for confirmation.

## Step 4: Write the rule

1. Class name: PascalCase of the slug plus `Rule` (`element-id-format` → `ElementIdFormatRule`).
2. Search `src/test/kotlin` for `override val id = "<slug>"`. If it exists, ask whether to replace it, keep it, or write a new class next to it.
3. Write the class from the template to `src/test/kotlin/<package>/architecture/bpmn/<ClassName>.kt`, or next to existing rules.

## Step 5: Register and verify

1. Add the rule to the existing model validation test (`*ModelValidationTest`) with `withRules(*BpmnRules.all().toTypedArray(), <ClassName>())`. If there is no such test, create it as shown in the template.
2. Run the test: `./gradlew test --tests '*ModelValidationTest'` or `./mvnw test -Dtest='*ModelValidationTest'`.
3. A compile error: fix the rule.
4. Violations in the models: report them. They are findings, not a broken rule. Ask whether to fix the models or to start the rule as `Severity.WARN`.
5. For a `hybrid` rule, remind the user that the semantic half still needs a review.
