package io.miragon.bpmn.adapter.outbound.codegen.writer

import io.miragon.bpmn.domain.BpmnModelApi

/**
 * Contributes one section of the generated Process API to a language-specific builder.
 *
 * Nothing is written anywhere here — [addTo] hands its section to an accumulating builder, and the file
 * only appears further along: the builder renders to a string, `buildApiFile` wraps that in a
 * `GeneratedApiFile`, and `ProcessApiFileSaver` is what finally touches the disk.
 *
 * Contributing is the only thing that differs between the Kotlin and the Java builder; *whether* a
 * section is contributed at all is decided once, by `ApiObjectSelection`.
 */
internal fun interface ObjectWriter<T> {

    fun addTo(builder: T, modelApi: BpmnModelApi)
}
