// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.dynamic

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.components.service
import software.aws.toolkits.jetbrains.utils.runUnderProgressIfNeeded
import software.aws.toolkits.resources.cloudformation.CloudFormationResourceType
import software.aws.toolkits.resources.message

class DynamicResourceSupportedTypes {

    private val supportedTypes by lazy {
        runUnderProgressIfNeeded(null, message("dynamic_resources.loading_manifest"), cancelable = false) {
            this.javaClass.getResourceAsStream("/cloudapi/dynamic_resources.json")?.use { resourceStream ->
                MAPPER.readValue<Map<String, ResourceDetails>>(resourceStream).mapKeys { (key, _) -> CloudFormationResourceType(key) }
            } ?: throw RuntimeException("dynamic resource manifest not found")
        }
    }

    fun getSupportedTypes(operation: PermittedOperation = PermittedOperation.LIST): List<CloudFormationResourceType> =
        supportedTypes.filterValues { it.operations.contains(operation) }.keys.toList()

    fun getDocs(resourceType: CloudFormationResourceType) = supportedTypes[resourceType]?.documentation

    companion object {
        fun getInstance(): DynamicResourceSupportedTypes = service()
        private val MAPPER = jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }
}
