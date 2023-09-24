// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.dynamic

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.components.service
import software.aws.toolkits.jetbrains.utils.runUnderProgressIfNeeded
import software.aws.toolkits.resources.message

interface DynamicResourceSupportedTypes {
    companion object {
        fun getInstance(): DynamicResourceSupportedTypes = service()
    }

    fun getSupportedTypes(): List<String>
    fun getDocs(resourceType: String): String?
}

class DefaultDynamicResourceSupportedTypes : DynamicResourceSupportedTypes {

    private val supportedTypes by lazy {
        runUnderProgressIfNeeded(null, message("dynamic_resources.loading_manifest"), cancelable = false) {
            this.javaClass.getResourceAsStream("/cloudapi/dynamic_resources.json")?.use { resourceStream ->
                MAPPER.readValue<Map<String, ResourceDetails>>(resourceStream)
            } ?: throw RuntimeException("dynamic resource manifest not found")
        }
    }

    override fun getSupportedTypes(): List<String> = supportedTypes.filterValues { it.operations.contains(PermittedOperation.LIST) }.keys.toList()

    override fun getDocs(resourceType: String) = supportedTypes[resourceType]?.documentation

    companion object {
        private val MAPPER = jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }
}
