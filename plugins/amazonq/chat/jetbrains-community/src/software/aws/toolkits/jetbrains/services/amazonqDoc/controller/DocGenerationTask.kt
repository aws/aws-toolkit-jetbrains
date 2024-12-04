// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqDoc.controller

import software.amazon.awssdk.services.codewhispererruntime.model.DocGenerationEvent
import software.amazon.awssdk.services.codewhispererruntime.model.DocGenerationFolderLevel
import software.amazon.awssdk.services.codewhispererruntime.model.DocGenerationInteractionType
import software.amazon.awssdk.services.codewhispererruntime.model.DocGenerationUserDecision
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger

class DocGenerationTask {
    // Telemetry fields
    var conversationId: String? = null
    var numberOfAddChars: Int? = null
    var numberOfAddLines: Int? = null
    var numberOfAddFiles: Int? = null
    var userDecision: DocGenerationUserDecision? = null
    var interactionType: DocGenerationInteractionType? = null
    var userIdentity: String? = null
    var numberOfNavigation = 0
    var folderLevel: DocGenerationFolderLevel? = DocGenerationFolderLevel.ENTIRE_WORKSPACE
    fun docGenerationEventBase(): DocGenerationEvent {
        val undefinedProps = this::class.java.declaredFields
            .filter { it.get(this) == null }
            .map { it.name }

        if (undefinedProps.isNotEmpty()) {
            val undefinedValue = undefinedProps.joinToString(", ")
            logger.debug { "DocGenerationEvent has undefined properties: $undefinedValue" }
        }

        return DocGenerationEvent.builder()
            .conversationId(conversationId)
            .numberOfAddChars(numberOfAddChars)
            .numberOfAddLines(numberOfAddLines)
            .numberOfAddFiles(numberOfAddFiles)
            .userDecision(userDecision)
            .interactionType(interactionType)
            .userIdentity(userIdentity)
            .numberOfNavigation(numberOfNavigation)
            .folderLevel(folderLevel)
            .build()
    }

    fun reset() {
        conversationId = null
        numberOfAddChars = null
        numberOfAddLines = null
        numberOfAddFiles = null
        userDecision = null
        interactionType = null
        userIdentity = null
        numberOfNavigation = 0
        folderLevel = null
    }

    companion object {
        private val logger = getLogger<DocGenerationTask>()
    }
}
