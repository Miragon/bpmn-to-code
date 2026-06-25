# tools

BPMN linting via [bpmnlint](https://github.com/bpmn-io/bpmnlint).

```bash
npm --prefix tools install        # one-time
npm --prefix tools run lint:bpmn      # full rules on shipped models
npm --prefix tools run lint:bpmn:di   # DI-only rules on testing fixtures
```

- `lint:bpmn` — `bpmnlint:recommended` (`.bpmnlintrc`) over `shared/bpmn/*.bpmn`
  and the web module's bundled samples.
- `lint:bpmn:di` — only the diagram-interchange rules (`.bpmnlintrc-di`:
  `no-bpmndi`, `no-overlapping-elements`) over the `bpmn-to-code-testing`
  fixtures. Those fixtures are intentionally semantically invalid in places, so
  they get the DI check only — not the full rule set.

Both run in CI via `.github/workflows/lint-bpmn-models.yml`.
