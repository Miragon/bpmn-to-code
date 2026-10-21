# GitHub Actions drift check

Only for generated code that is committed in a source folder. The job regenerates the Process API and fails if the working tree changed. Generation is deterministic, so a Linux runner produces the same bytes as the developer's machine.

## Workflow

```yaml
# .github/workflows/verify-process-api.yml
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

      # setup steps of the build tool, see below

      - name: Regenerate the Process API
        run: <regenerate command>

      - name: Fail if the generated API is out of date
        run: |
          if [ -n "$(git status --porcelain)" ]; then
            echo "::error::Generated Process API is out of date. Run '<regenerate command>' and commit the result."
            git --no-pager diff
            exit 1
          fi
```

- Use the default branch of the repository for `branches`.
- If an existing workflow already builds the project on pull requests, add the two steps to the end of that job instead of a new file.
- Reuse the action versions, the pinning style and the JDK version of the existing workflows.

## Per build tool

| Build tool | Setup steps | `<regenerate command>` |
|---|---|---|
| Gradle | `actions/setup-java@03ad4de0992f5dab5e18fcb136590ce7c4a0ac95 # v5` (`java-version: '21'`, `distribution: 'temurin'`), `gradle/actions/setup-gradle@3f131e8634966bd73d06cc69884922b02e6faf92 # v6` | `./gradlew generateBpmnModelApi --rerun` |
| Maven | `actions/setup-java` as above, with `cache: maven` | `mvn -B generate-sources` (`./mvnw` if the project has a wrapper) |
| CLI | the download step below | the generation script or `bpmn-to-code generate-api …` call from [cli.md](cli.md) |

**Gradle needs `--rerun`.** Gradle does not track output outside the build directory, so without it a hand-edited or deleted generated file goes unnoticed. `--rerun` applies to the task right before it: in a multi-module build or with several tasks, repeat it per task, e.g. `./gradlew :service:generateBpmnModelApi --rerun :service:generateZeebeApi --rerun`.

**Maven** with an execution not bound to `generate-sources`: `mvn -B bpmn-to-code:generate-bpmn-api`.

**CLI**: download the Linux archive of the version the project uses and put it on the `PATH`. Keep it out of the checkout, or the check sees it as an untracked file:

```yaml
      - name: Install bpmn-to-code
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          gh release download v<version> --repo Miragon/bpmn-to-code --pattern '*-linux-x64.zip' --dir "$RUNNER_TEMP"
          unzip -q "$RUNNER_TEMP/bpmn-to-code-<version>-linux-x64.zip" -d "$RUNNER_TEMP"
          echo "$RUNNER_TEMP/bpmn-to-code-<version>-linux-x64/bin" >> "$GITHUB_PATH"
```

## Why `git status --porcelain`

`git diff --exit-code` ignores untracked files. With `--porcelain`, the check also catches a new model whose API was never committed, and a deleted model whose API regeneration removed.
