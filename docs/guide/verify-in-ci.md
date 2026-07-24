# Verify Generated Code in CI

When you commit your generated Process API, it can silently drift from your BPMN models.
Someone edits a `.bpmn` file, forgets to regenerate, and the committed API no longer matches.

You don't need a dedicated plugin feature to catch this.
Because generation is **deterministic**, a CI job can regenerate the API and fail if the result differs from what's committed.

## Prerequisites

- **Generated code is committed.**
  Your `outputFolderPath` points at a tracked source directory (e.g. `$projectDir/src/main/kotlin`), so there's a reference state to diff against.
  See [Configuration](/guide/configuration).
- **Generation is deterministic.**
  bpmn-to-code produces byte-identical output for the same input, so a re-run only changes files when the models actually changed.

## GitHub Actions

Regenerate the API on every pull request and fail if it produces any change:

```yaml
name: Verify Process API

on:
  pull_request:
    branches: [ main ]

jobs:
  verify-process-api:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803 # v6

      - name: Set up JDK 21
        uses: actions/setup-java@03ad4de0992f5dab5e18fcb136590ce7c4a0ac95 # v5
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@3f131e8634966bd73d06cc69884922b02e6faf92 # v6

      - name: Regenerate the Process API
        run: ./gradlew generateBpmnModelApi

      - name: Fail if the generated API is out of date
        run: |
          if [ -n "$(git status --porcelain)" ]; then
            echo "::error::Generated Process API is out of date. Run './gradlew generateBpmnModelApi' and commit the result."
            git --no-pager diff
            exit 1
          fi
```

::: tip Pin your actions
For supply-chain safety, pin each action to a full commit SHA (e.g. `actions/checkout@df4cb1c...  # v6`) rather than a mutable tag.
:::

## Why `git status --porcelain` and not `git diff --exit-code`

`git diff --exit-code` only inspects **tracked** files.
Add a new BPMN model and commit it without its generated API, and regeneration creates a **new, untracked** file — which `git diff` ignores, so the check passes even though the API is missing.

`git status --porcelain` reports modified *and* untracked files, so it catches both a changed API and a missing one.
It prints nothing when the working tree is clean.

## Known limitation: orphaned files

This check does **not** detect orphaned generated files.
If you delete a BPMN model, its previously generated API file stays on disk.
bpmn-to-code only writes files, it never deletes them, so regeneration won't remove the stale file and the diff stays clean.
You'd need to spot and delete orphans yourself.

::: info
If you hit a case a CI pipe can't cover — orphan detection being the main one — that's the signal a dedicated check/verify mode in the plugin would be worth building.
See [issue #5](https://github.com/Miragon/bpmn-to-code/issues/5).
:::
