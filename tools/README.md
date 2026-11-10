# tools

BPMN linting with [bpmnlint](https://github.com/bpmn-io/bpmnlint) for the models in this repository.

```bash
npm --prefix tools install
```

## Full linting

Runs the `bpmnlint:recommended` rule set from `.bpmnlintrc` over the models in `shared/bpmn`. All modules test against these models, and the web app bundles the bike-leasing models as its examples.

```bash
npm --prefix tools run lint:bpmn
```

## Reduced linting

Runs only the diagram interchange rules from `.bpmnlintrc-di`, `no-bpmndi` and `no-overlapping-elements`, over the fixtures of `bpmn-to-code-testing` and `bpmn-to-code-core`. Those fixtures are invalid on purpose in places, so the full rule set does not fit; the reduced check only makes sure every element has a shape and nothing overlaps.

```bash
npm --prefix tools run lint:bpmn:di
```

## CI

Both checks run in `.github/workflows/lint-bpmn-models.yml` on every pull request that touches a `.bpmn` file or the `tools` folder.
