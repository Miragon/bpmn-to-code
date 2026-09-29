package io.miragon.bpmn.domain

class DuplicateProcessIdException(processId: String, fileNames: List<String>) :
    IllegalArgumentException(
        "Process ID '$processId' is defined in multiple BPMN files: ${fileNames.sorted().joinToString()}. " +
            "Set enableVariants = true to merge them into variants.",
    )
