# 12. Web application

## Context

Not everyone who wants to see what bpmn-to-code produces has a build to add a plugin to. A browser application lets people try the generator with their own model, and a REST API lets tools call it. Some users must not send BPMN files to a third party, so the same application has to be runnable in their own infrastructure. The hosted instance is a public service on small hardware and receives little traffic.

## Decision

**The web application is one more thin entry point.** It calls core's in-memory plugins ([record 2](002-hexagonal-core.md)) and adds no generation logic, so its output equals that of the build plugins ([record 1](001-product-surfaces-and-distribution.md)). It serves code generation and the JSON export, each as a REST endpoint with an OpenAPI description, and the browser frontend on top of them.

**It is stateless.** A request carries the BPMN files and the choice of engine and language; the response carries the generated files. Nothing is stored. The package name of generated code is fixed. For Kotlin and Java the response also offers the sources of the runtime library, so a download compiles without a dependency.

**Backend and frontend ship as one artifact.** Ktor serves the API and a static frontend from one fat jar. The frontend is plain HTML, CSS and JavaScript without a build step. The image is a distroless Java 21 image that runs as non-root and is configured through environment variables only.

**Third-party browser libraries come from public CDNs.** The page loads the BPMN viewer (bpmn-js), syntax highlighting (highlight.js) and ZIP creation (JSZip) from unpkg and cdnjs, and its fonts from Google Fonts, at runtime in the user's browser.

**Requests are bounded.** A request may carry at most three files. A model the generator rejects is answered with status 400 and the reason, any other failure with 500 and no detail.

**The generator is warmed up at start.** A daemon thread generates the bundled example models in every output language and as JSON several times before the first request arrives. The application serves too few requests for the JVM to compile the generator's code on its own, so without the warm-up every request would run slowly.

Usage, the REST API and the environment variables are documented in [Web app](/web/); building and publishing the image in [Releasing](/contributing/releasing).

## Consequences

- There is one build, one artifact and one container, and no Node toolchain in the repository.
- BPMN files stay inside a self-hosted installation, but its users' browsers still contact the CDN hosts. Where they cannot, at least the diagram preview, the highlighting and the ZIP download are unavailable; only the missing ZIP library is reported to the user. Loading fonts from Google is a data-protection topic an operator has to assess.
- CORS allows every origin unless the operator sets a list.
- The published image is built for `linux/amd64` only.
- The OpenAPI description relies on a Ktor API that is still marked experimental.
- The bundled example models are the shared test models of the repository, so they are production artifacts, not only fixtures.
- Performance claims about the hosted instance are measured with the benchmark module ([Benchmark](/contributing/benchmark)), which has a mode without JIT compilation for this reason.

## Rejected alternatives

- **A separate single-page application module.** A second build, toolchain and artifact for an upload-and-download interface. This is the option to revisit when the frontend grows.
- **Spring Boot.** A larger footprint than an application with two generation endpoints needs.
