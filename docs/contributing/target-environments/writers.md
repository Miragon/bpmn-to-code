# Writers

Undecided. Both ways work.

Kotlin and Java keep their poet libraries. The question is how every further language is written. The same TypeScript excerpt was built both ways in a spike; both produced the identical file.

| Option | What it means | Consequence |
|---|---|---|
| C1 Writer in Kotlin | As C# today: Kotlin code that appends lines | The compiler checks everything. A new kind of successor that a writer does not handle is a build error. The shape of the output is not visible at a glance |
| C2 Template file | One file per output file, for example FreeMarker | Simple parts read like the output. Branches become long lines, type names turn into text, and a mistake shows up when the template runs, or not at all. Adds a dependency to every user's build classpath |
| C3 Both | Fixed blocks such as a file header or runtime types as text files, everything with logic in Kotlin | Close to what C# does today |

The same branch, once in each form:

```kotlin
private fun edgeLiteral(edge: SuccessorEdge): String {
    return when (edge) {
        is SuccessorEdge.ViaSequenceFlows -> {
            val flows = edge.flows.joinToString(", ") { flow -> flowLiteral(flow) }
            "{ kind: \"sequenceFlow\", flows: [$flows] }"
        }
        is SuccessorEdge.AttachedBoundaryEvent -> "{ kind: \"boundaryEvent\" }"
        is SuccessorEdge.AssociatedCompensationHandler -> "{ kind: \"compensationHandler\" }"
    }
}
```

```text
<#if edge.class.simpleName == "ViaSequenceFlows">{ kind: "sequenceFlow", flows: [<#list edge.flows as flow>...</#list>] }
<#elseif edge.class.simpleName == "AttachedBoundaryEvent">{ kind: "boundaryEvent" }
<#else>{ kind: "compensationHandler" }</#if>
```

Either way the choice is internal. It does not change what users see, and it does not depend on the [language of core](./core-language).

Back to the [overview](./).
