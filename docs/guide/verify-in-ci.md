# Verify Generated Code in CI

When the generated Process API is committed, it can drift from the BPMN models: someone edits a `.bpmn` file and forgets to regenerate. A CI job catches this by regenerating and failing if anything changed.

This works because generation is **deterministic**: the output depends on the `.bpmn` files and the configuration alone, not on the operating system or the filesystem that read them. A macOS developer and a Linux runner produce the same bytes.

## GitHub Actions

The check needs the generated code in a tracked directory, such as `outputFolderPath = "src/main/kotlin"`.

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
        run: ./gradlew generateBpmnModelApi --rerun

      - name: Fail if the generated API is out of date
        run: |
          if [ -n "$(git status --porcelain)" ]; then
            echo "::error::Generated Process API is out of date. Run './gradlew generateBpmnModelApi --rerun' and commit the result."
            git --no-pager diff
            exit 1
          fi
```

**`--rerun` is required.** Outside the build directory, Gradle does not track the generated files: it considers the task up to date as long as the BPMN files and the configuration are unchanged, even when a generated file was edited or deleted by hand. `--rerun` forces the generation. See [Generate as part of the build](/getting-started/gradle#generate-as-part-of-the-build).

**Maven** has no up-to-date check, so the goal always generates. Replace the Gradle steps with `mvn generate-sources`, or `mvn bpmn-to-code:generate-bpmn-api` when the execution is not [bound to a phase](/getting-started/maven).

## What the check catches

- **A changed API**: a model was edited and the committed code is stale.
- **A missing API**: a new model was committed without its generated file. That file is untracked after regeneration, which is why the check uses `git status --porcelain` and not `git diff --exit-code`, which ignores untracked files.
- **An orphaned API**: a model was deleted or renamed. Regeneration [removes the stale file](/guide/generated-api#shared-definitions), and `git status` reports the deletion.

The same check works for the [JSON export](/surface/json) with `generateBpmnModelJson --rerun`, with one gap: stale `.json` files are not removed, so an orphaned one goes unnoticed.
