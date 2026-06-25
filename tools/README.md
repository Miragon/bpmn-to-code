# tools

BPMN linting via [bpmnlint](https://github.com/bpmn-io/bpmnlint).

```bash
npm --prefix tools install
```

## Full linting

This runs the full `bpmnlint:recommended` rule set from `.bpmnlintrc`,
over the models we ship in production.
Those are the models under `shared/bpmn` and the bundled samples of the web module.

```bash
npm --prefix tools run lint:bpmn
```

## Reduced linting

This runs only the diagram interchange rules from `.bpmnlintrc-di`,
namely `no-bpmndi` and `no-overlapping-elements`,
over the fixtures of `bpmn-to-code-testing`.
Those fixtures are intentionally invalid in places,
so the full rule set does not fit.
The reduced check only makes sure they are valid in a visual manner,
which means every element has a shape and nothing overlaps.

```bash
npm --prefix tools run lint:bpmn:di
```

## Pipeline

Both checks run in CI through `.github/workflows/lint-bpmn-models.yml`,
on every pull request that touches a `.bpmn` file or the `tools` folder.
This ensures the models are validated before they are merged to `main`.
