// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.openapi.project.Project
import com.intellij.ui.TextFieldWithAutoCompletion.StringsCompletionProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.amazon.awssdk.services.resourcegroupstaggingapi.ResourceGroupsTaggingApiClient
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope

class KeyProvider(project: Project) :
    StringsCompletionProvider(listOf(), null),
    CoroutineScope by ApplicationThreadPoolScope("completionProvider") {

    init {
        launch {
            val client: ResourceGroupsTaggingApiClient = project.awsClient()
            val items = client.tagKeysPaginator.tagKeys().toMutableList()
            setItems(items)
        }
    }
}

class ValueProvider(private val project: Project, private val key: String) :
    StringsCompletionProvider(listOf(), null),
    CoroutineScope by ApplicationThreadPoolScope("completionProvider") {

    override fun getPrefix(text: String, offset: Int): String? {
        var completed = 0
        val chunks = text.split(",")
        if (chunks.size <= 1) {
            return text
        }
        chunks.forEach { chunk ->
            // check if we are in this chunk
            if (completed + chunk.length >= offset) {
                return chunk.trim()
            } else {
                // +1 for the ','
                completed += chunk.length + 1
            }
        }
        // as a fallback return the last chunk
        return chunks.last()
    }

    init {
        launch {
            val client: ResourceGroupsTaggingApiClient = project.awsClient()
            // TODO make this actually work
            val items = client.getTagValuesPaginator { it.key(key) }.tagValues().toMutableList()
            setItems(items)
        }
    }
}
