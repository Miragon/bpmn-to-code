# tools

BPMN linting via [bpmnlint](https://github.com/bpmn-io/bpmnlint).

```bash
npm --prefix tools install   # one-time
```

## Full linting

Production-grade check: the full `bpmnlint:recommended` rule set (`.bpmnlintrc`)
over the models we ship — `shared/bpmn/*.bpmn` and the web module's bundled
samples.

```bash
npm --prefix tools run lint:bpmn
```

## Reduced linting

Only the diagram-interchange rules (`.bpmnlintrc-di`: `no-bpmndi`,
`no-overlapping-elements`) over the `bpmn-to-code-testing` fixtures. Those
fixtures are intentionally semantically invalid in places, so the full rule set
does not fit — this just ensures they are visually valid (every element has a
shape, nothing overlaps).

```bash
npm --prefix tools run lint:bpmn:di
```

## Pipeline

Both checks run in CI via `.github/workflows/lint-bpmn-models.yml` on every pull
request that touches a `.bpmn` file or `tools/`, so models are validated before
they are merged to `main`.
