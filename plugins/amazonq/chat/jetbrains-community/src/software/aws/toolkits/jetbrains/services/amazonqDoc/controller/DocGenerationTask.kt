// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqDoc.controller

import software.amazon.awssdk.services.codewhispererruntime.model.DocFolderLevel
import software.amazon.awssdk.services.codewhispererruntime.model.DocInteractionType
import software.amazon.awssdk.services.codewhispererruntime.model.DocUserDecision
import software.amazon.awssdk.services.codewhispererruntime.model.DocV2AcceptanceEvent
import software.amazon.awssdk.services.codewhispererruntime.model.DocV2GenerationEvent
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger

class DocGenerationTasks {
    private val tasks: MutableMap<String, DocGenerationTask> = mutableMapOf()

    fun getTask(tabId: String): DocGenerationTask = tasks.getOrPut(tabId) { DocGenerationTask() }

    fun deleteTask(tabId: String) {
        tasks.remove(tabId)
    }
}

class DocGenerationTask {
    var mode: Mode = Mode.NONE

    // Telemetry fields
    var conversationId: String? = null
    var numberOfAddedChars: Int? = null
    var numberOfAddedLines: Int? = null
    var numberOfAddedFiles: Int? = null
    var numberOfGeneratedChars: Int? = null
    var numberOfGeneratedLines: Int? = null
    var numberOfGeneratedFiles: Int? = null
    var userDecision: DocUserDecision? = null
    var interactionType: DocInteractionType? = null
    var numberOfNavigations: Int = 0
    var folderLevel: DocFolderLevel = DocFolderLevel.ENTIRE_WORKSPACE
    fun docGenerationEventBase(): DocV2GenerationEvent {
        val undefinedProps = this::class.java.declaredFields
            .filter { it.get(this) == null }
            .map { it.name }

        if (undefinedProps.isNotEmpty()) {
            val undefinedValue = undefinedProps.joinToString(", ")
            logger.debug { "DocV2GenerationEvent has undefined properties: $undefinedValue" }
        }

        return DocV2GenerationEvent.builder()
            .conversationId(conversationId)
            .numberOfGeneratedChars(numberOfGeneratedChars)
            .numberOfGeneratedLines(numberOfGeneratedLines)
            .numberOfGeneratedFiles(numberOfGeneratedFiles)
            .interactionType(interactionType)
            .numberOfNavigations(numberOfNavigations)
            .folderLevel(folderLevel)
            .build()
    }

    fun docAcceptanceEventBase(): DocV2AcceptanceEvent {
        val undefinedProps = this::class.java.declaredFields
            .filter { it.get(this) == null }
            .map { it.name }

        if (undefinedProps.isNotEmpty()) {
            val undefinedValue = undefinedProps.joinToString(", ")
            logger.debug { "DocV2AcceptanceEvent has undefined properties: $undefinedValue" }
        }

        return DocV2AcceptanceEvent.builder()
            .conversationId(conversationId)
            .numberOfAddedChars(numberOfAddedChars)
            .numberOfAddedLines(numberOfAddedLines)
            .numberOfAddedFiles(numberOfAddedFiles)
            .userDecision(userDecision)
            .interactionType(interactionType)
            .numberOfNavigations(numberOfNavigations)
            .folderLevel(folderLevel)
            .build()
    }

    fun reset() {
        conversationId = null
        numberOfAddedChars = null
        numberOfAddedLines = null
        numberOfAddedFiles = null
        numberOfGeneratedChars = null
        numberOfGeneratedLines = null
        numberOfGeneratedFiles = null
        userDecision = null
        interactionType = null
        numberOfNavigations = 0
        folderLevel = DocFolderLevel.ENTIRE_WORKSPACE
    }

    companion object {
        private val logger = getLogger<DocGenerationTask>()
    }
}
