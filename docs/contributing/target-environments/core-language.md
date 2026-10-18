# The Language of Core

Decided: **A1, core stays Kotlin**, behind a command-line tool.

The question that started this: would a core in Go or Node make more languages easier, because those ecosystems have better code generation libraries?

They do not. A library like KotlinPoet exists for one output language each. No ecosystem has one for every language. Go generators use `text/template`, Node generators use template strings or Handlebars, OpenAPI Generator uses Mustache from Java. Writing a language without such a library means assembling text in every ecosystem, and core already does that for C#.

| Option | What it means | Consequence |
|---|---|---|
| A1 Core stays Kotlin, behind a command-line tool | Nothing is rewritten. Non-JVM teams get the tool through their package manager | Gradle and Maven users notice nothing. The JVM has to travel with the tool, see [delivery](./delivery#carrying-java-along) |
| A2 Kotlin Multiplatform | Core compiles to JVM and to JavaScript. The BPMN reader (`camunda-bpmn-model`) and JavaPoet have no non-JVM version and have to be replaced by own code | A real JavaScript package, usable at runtime and in a browser. Months of work, and the byte-identical output of Java has to be reproduced by a new writer |
| A3 Rewrite in Go or TypeScript | All of core and its tests are written again | Smallest tool, fastest start. Every existing user gets a downloaded binary or an embedded interpreter in their build. Custom validation rules, which are Kotlin classes against core's `domain` package, need a bridge. Many months before anything new exists |

Generation at build time is [settled as enough](./#what-is-settled-in-principle), so nothing requires A2 or A3 today. They become relevant when a team needs to call the generator at runtime.

Back to the [overview](./).
