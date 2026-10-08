---
name: bpmn-to-code-validate-docs
description: "Check that docs, READMEs, CLAUDE.md, repo skills and context7.json still match the code. Use before a release, after changing plugin parameters, validation rules, engines, output languages, web routes, modules or workflows, and whenever docs were moved or deleted."
---

# Validate docs against the code

The code is the source of truth. Report every mismatch; fix the docs when the code is clearly intended, and ask when it could just as well be a bug in the code.

Scope: `docs/**/*.md` (not `node_modules`), `README.md`, `*/README.md`, `CLAUDE.md`, `.claude/rules/**`, `.claude/skills/**`, `bpmn-to-code-skills/**/*.md`, `context7.json`. Never edit `CHANGELOG.md`.

## 1. Mechanical checks

Run these first; they need no judgement.

1. **Docs build.** `cd docs && npm ci && npm run build` must pass. VitePress fails on dead links inside `docs/`.
2. **Links outside the site.** For every relative markdown link in the READMEs, `CLAUDE.md` and the skills: the target file exists. For every `https://miragon.github.io/bpmn-to-code/<path>` link anywhere: `docs/<path>.md` or `docs/<path>/index.md` exists.
3. **Repo paths.** Every backticked token in scope that looks like a repo path (contains `/` and starts with a top-level folder or dot-folder of this repo) exists:

   ```bash
   tops=$(ls -A | paste -sd'|' -)
   grep -rnoE "\`($tops)/[A-Za-z0-9_./{},*<>-]+\`" README.md CLAUDE.md */README.md .claude docs --include='*.md' --exclude-dir=node_modules \
     | while IFS= read -r hit; do
         target=$(printf '%s' "$hit" | sed -E 's/^[^`]*`//; s/`$//')
         case "$target" in *[\*\<\{]*) continue ;; esac
         [ -e "$target" ] || echo "MISSING $hit"
       done
   ```

   Paths with `*`, `<…>` or `{…}` are skipped by the script; check them by eye. Hits below `build/` are build outputs and fine.
4. **Class and function names.** Every backticked `CamelCase` type or `function()` named in `CLAUDE.md`, `docs/contributing/**` and `.claude/skills/**` is found by `grep -rn` in the sources.
5. **Sidebar.** Every `link:` in `docs/.vitepress/config.mts` resolves to a file, and every page under `docs/` except the ADRs is reachable from the sidebar or linked from another page.
6. **Versions.** `grep -rn "$(grep projectVersion= gradle.properties | cut -d= -f2)"` over the scope: every hit is in a file listed under `extra-files` in `release-please-config.json` and sits between release-please markers. Every `extra-files` entry exists. Older version literals outside `docs/changelog/` and the benchmark page are suspicious.
7. **Deleted pages.** No link or sidebar entry points at a page that `git status` or `git log --diff-filter=D -- docs` shows as removed.

## 2. Fact tables

Read the source, then compare every place in scope that states the fact. Search for the values, not only on the page you expect them.

| Fact | Source of truth | What to compare |
|---|---|---|
| Process engines | `ProcessEngine` in core's `domain/shared` | names and count; CIB seven is documented as `CAMUNDA_7` |
| Output languages | `OutputLanguage` in core's `domain/shared` | names; C# is "experimental", never "beta" |
| Gradle tasks | `bpmn-to-code-gradle/src/main/kotlin` | task names, parameters, conventions, experimental markers, no lifecycle wiring, automatic runtime dependency |
| Maven goals | `bpmn-to-code-maven/src/main/java` | goal names, parameters, `defaultValue`s, `defaultPhase` (snippets must bind a phase when it is `NONE`), manual runtime dependency |
| Validation rules | `BpmnRules` in `bpmn-to-code-testing`, rule classes in core's `domain/validation/rules`, built-in list in `BpmnValidationService` | ids, severities, `mandatory`, default versus opt-in, build-time set versus `BpmnRules.all()`, any stated rule count |
| Web app | routing and `System.getenv` calls in `bpmn-to-code-web/src/main/kotlin`, the `Dockerfile` | routes, environment variables really read, file limit, fixed package; `bpmn-to-code-web/README.md` is the Docker Hub description and must stand alone |
| Shipped skills | `bpmn-to-code-skills/skills` and `bpmn-to-code-skills/agents` | lists in `bpmn-to-code-skills/README.md`, `docs/skills/`, `context7.json` |
| Modules | `settings.gradle.kts`, top-level folders, `publish-*.yml` | module lists and publication targets in `CLAUDE.md` and `docs/contributing/architecture.md` |
| ADRs | files in `docs/contributing/adr` | the ADR index; every ADR reference in docs and KDoc (`grep -rn "ADR [0-9]" --include='*.kt'`) |
| Quality gates | root and module build files, `lefthook.yml`, `.github/workflows` | coverage and PIT thresholds, hook versus CI, PR title types in `docs/contributing/index.md` |
| Release flow | `release-please.yml`, `publish-*.yml`, `update-ops-deployment.yml` | `docs/contributing/releasing.md` |
| Generated API | golden files in `bpmn-to-code-core/src/test/resources/api` | samples and section names in `docs/guide/` and the README (the node tree is `FlowNodes`) |
| Process JSON | `docs/public/schema`, goldens in `bpmn-to-code-core/src/test/resources/json` | `formatVersion`, schema URL and samples in `docs/surface/json.md` |
| Engine extraction | dialects in core's `adapter/outbound/engine/dialect` | what `docs/engines/` says each engine reads |
| Test helpers | `bpmn-to-code-core/src/test/kotlin/io/miragon/bpmn/domain` | builder names in docs and `.claude/skills/create-unit-test` |

Examples must use the MiraVelo models from `shared/bpmn`; flag any other domain or a customer name.

## 3. Report

One table, mismatches only, then the fixes you made:

```
| File:line | States | Code says (source) | Action |
|---|---|---|---|
| README.md:46 | 12 built-in rules | 13 in BpmnRules.all() | fixed |
```

Finish with the mechanical checks that passed, so it is clear they ran.
