# 13. Process JSON deprecation

## Context

The Process JSON ([record 9](009-process-json-contract.md)) was meant for consumers that want the structure of a process without parsing BPMN XML: AI assistants, reviewers reading a process change as a text diff, tools in languages the generator does not target.

Since 6.0 the document follows the BPMN 2.0 vocabulary so closely that it is BPMN XML without the diagram, and the generated API carries almost the same facts. Nothing in the project consumes it. As input for generators in other languages it is not sufficient: variables, shared definitions and generated names are missing. Its status was contradictory: the documentation called it beta, [record 11](011-compatibility-policy.md) listed it as a stable contract. Every new BPMN facet still has to be added to the JSON mapping, the schema, the golden files and the documentation.

## Decision

**The Process JSON is deprecated in 6.x and will be removed in 7.0**, unless users report a use case before then.

**Every entry point says so.** The Gradle task `generateBpmnModelJson` and the Maven goal `generate-bpmn-json` log a warning on every run. The web endpoint `POST /api/generate-json` answers with a `Deprecation` header and is marked deprecated in its OpenAPI description, and the tab in the web application carries a "deprecated" tag.

**The output does not change until the removal.** Format, schema and file names stay as record 9 describes them.

**Feedback has one place.** Every notice points to [issue #206](https://github.com/Miragon/bpmn-to-code/issues/206), which tracks the removal.

**The documentation no longer builds on it.** The landing page and "Why bpmn-to-code" present the generated API, validation and the agent skills; the format reference stays, marked as deprecated.

## Consequences

- Users of the task or the goal see a warning in every build until they remove it.
- The JSON mapping, the schema and the golden files still have to follow new BPMN facets until 7.0.
- `retries`, the connector `templateId`, the message `correlationKey` and the raw extension elements are visible only in the JSON. The removal has to decide for each whether it moves into the generated API, stays for validation rules or is dropped.
- The schema URL has to keep being served after the removal, because documents written by 6.x name it.

## Rejected alternatives

- **Remove it in a 6.x minor release.** It is a published format with a schema; [record 11](011-compatibility-policy.md) allows breaking it only in a major release.
- **Declare it stable and keep it.** A public format needs a consumer that justifies the upkeep, and none is known.
- **Extend it until it can feed generators in other languages.** That is a different format, and it would duplicate the model the generator already has in memory ([record 7](007-output-languages.md)).
