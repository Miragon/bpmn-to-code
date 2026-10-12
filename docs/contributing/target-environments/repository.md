# Further Changes

What the concept changes beyond the [folders a language package adds](./language-package#where-the-parts-live-in-the-repository). This assumes that [core stays Kotlin](./core-language).

| Area | Today | Then |
|---|---|---|
| Shared definitions | Every language reads the domain itself | One model for all languages, next to the flow graph |
| Release | Maven Central, Gradle Plugin Portal, Docker Hub | plus one registry per delivery channel, each with its own credentials and publishing step |
| Versions | One version for all artifacts | The same version for the packages in the other registries |

## ADRs this touches

| ADR | Why |
|---|---|
| [001](../adr/001-product-surfaces-and-distribution) | New product surfaces: the command-line tool and each delivery channel |
| [007](../adr/007-output-languages) | How further languages are written |
| [011](../adr/011-compatibility-policy) | New public contracts: the command line and each new output |
| [012](../adr/012-web-application) | A Node toolchain in the repository once TypeScript output is type-checked |

## Diagram sources

The diagrams of this concept have an editable source: the `.excalidraw` files in `docs/contributing/images/` open in [Excalidraw](https://excalidraw.com). After a change, export the diagram as SVG over the file of the same name.

Back to the [overview](./).
