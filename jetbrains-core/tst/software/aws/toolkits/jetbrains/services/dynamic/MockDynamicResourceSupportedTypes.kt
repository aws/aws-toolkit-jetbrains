// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.dynamic

import com.intellij.openapi.components.service
import com.intellij.testFramework.ApplicationRule
import org.jetbrains.annotations.VisibleForTesting

class MockDynamicResourceSupportedTypes : DynamicResourceSupportedTypes {

    @VisibleForTesting
    internal val supportedTypes = mutableMapOf<String, ResourceDetails>()

    override fun getSupportedTypes(): List<String> = supportedTypes.keys.toList()

    override fun getDocs(resourceType: String): String? = supportedTypes[resourceType]?.documentation

    companion object {
        fun getInstance(): MockDynamicResourceSupportedTypes = service<DynamicResourceSupportedTypes>() as MockDynamicResourceSupportedTypes
    }
}

class DynamicResourceSupportedTypesRule : ApplicationRule() {
    private val instance by lazy { MockDynamicResourceSupportedTypes.getInstance() }

    fun addTypes(types: Set<String>) {
        types.forEach { type ->
            instance.supportedTypes[type] = ResourceDetails(emptyList(), null, null)
        }
    }

    override fun after() {
        instance.supportedTypes.clear()
    }
}
