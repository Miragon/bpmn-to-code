package io.miragon.bpmn.adapter.outbound.codegen.navigation

import io.miragon.bpmn.domain.shared.FlowNodeDefinition

/**
 * A parsed [FlowNodeDefinition] paired with its guaranteed-non-null id.
 *
 * The domain models `id` as nullable so a node without an id survives parsing and is reported by the
 * mandatory `missing-element-id` validation rule instead of crashing the pipeline. By the time the navigation
 * graph is built that rule has passed, so every node has an id. This wrapper captures that invariant once, at
 * the graph boundary: the whole factory then works with a non-null [id] and no downstream step needs a `!!`.
 */
internal data class FlowNodeWithId(
    val id: String,
    val definition: FlowNodeDefinition,
)
