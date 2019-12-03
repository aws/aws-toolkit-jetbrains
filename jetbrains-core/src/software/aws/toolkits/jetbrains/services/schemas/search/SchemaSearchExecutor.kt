// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.schemas.search

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.schemas.SchemasClient
import software.amazon.awssdk.services.schemas.model.SearchSchemasRequest
import software.amazon.awssdk.services.schemas.model.SearchSchemasResponse
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.core.AwsResourceCache
import software.aws.toolkits.jetbrains.services.schemas.resources.SchemasResources

class SchemaSearchExecutor(
    private val project: Project,
    private val schemasClient: SchemasClient = AwsClientManager.getInstance(project).getClient()
) {

    fun searchSchemasInRegistry(
        registryName: String,
        searchText: String,
        incrementalResultsCallback: OnSearchResultReturned<SchemaSearchResult>,
        registrySearchErrorCallback: OnSearchResultError
    ) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val results = doSingleSearch(registryName, searchText)
                incrementalResultsCallback(results.map { SchemaSearchResult(it.name, it.versions) })
            } catch (e: Exception) {
                LOG.warn(e) { "SchemaSearchExecutor exception searching schema registry" }
                registrySearchErrorCallback(SchemaSearchError(registryName, e.message ?: ""))
            }
        }
    }

    fun searchSchemasAcrossAllRegistries(
        searchText: String,
        incrementalResultsCallback: OnSearchResultReturned<SchemaSearchResultWithRegistry>,
        registrySearchErrorCallback: OnSearchResultError
    ) {
        AwsResourceCache.getInstance(project).getResource(SchemasResources.LIST_REGISTRIES)
            .thenApply { registries ->
                registries.parallelStream()
                    .forEach {
                        val registryName = it.registryName()
                        ApplicationManager.getApplication().executeOnPooledThread {
                            try {
                                val singleSearchResults = doSingleSearch(registryName, searchText)
                                incrementalResultsCallback(singleSearchResults)
                            } catch (e: Exception) {
                                LOG.warn(e) { "SchemaSearchExecutor exception searching schema registry" }
                                registrySearchErrorCallback(SchemaSearchError(registryName, e.message ?: ""))
                            }
                        }
                    }
            }
    }

    private fun doSingleSearch(
        registryName: String,
        searchText: String
    ): List<SchemaSearchResultWithRegistry> {
        val searchRequest = SearchSchemasRequest.builder()
            .registryName(registryName)
            .keywords(searchText)
            .build()

        val resultsResponse: SearchSchemasResponse = schemasClient.searchSchemas(searchRequest)

        return resultsResponse.schemas().map { searchSchemaSummary ->
            val sortedVersions = searchSchemaSummary.schemaVersions()
                .map { it.schemaVersion() }
                .sortedByDescending { it.toIntOrNull() }

            if (sortedVersions.isEmpty()) {
                null
            } else {
                SchemaSearchResultWithRegistry(searchSchemaSummary.schemaName(), sortedVersions, searchSchemaSummary.registryName())
            }
        }.filterNotNull()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(SchemaSearchExecutor::class.java)
    }
}
