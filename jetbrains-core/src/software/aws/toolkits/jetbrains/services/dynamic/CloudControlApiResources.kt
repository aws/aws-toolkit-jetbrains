// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.dynamic

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import software.amazon.awssdk.arns.Arn
import software.amazon.awssdk.services.cloudcontrol.CloudControlClient
import software.amazon.awssdk.services.cloudformation.CloudFormationClient
import software.amazon.awssdk.services.cloudformation.model.RegistryType
import software.amazon.awssdk.services.cloudformation.model.Visibility
import software.aws.toolkits.jetbrains.core.ClientBackedCachedResource
import software.aws.toolkits.jetbrains.core.Resource
import software.aws.toolkits.jetbrains.core.map
import software.aws.toolkits.jetbrains.services.s3.resources.S3Resources
import software.aws.toolkits.resources.cloudformation.AWS
import software.aws.toolkits.resources.cloudformation.CloudFormationResourceType

object CloudControlApiResources {
    fun listResources(typeName: CloudFormationResourceType): Resource<List<DynamicResource>> =
        when (typeName) {
            AWS.S3.Bucket -> S3Resources.LIST_BUCKETS.map { it.name() }
            else -> ClientBackedCachedResource(CloudControlClient::class, "cloudcontrolapi.dynamic.resources.$typeName") {
                this.listResourcesPaginator { req -> req.typeName(typeName.fullName) }
                    .flatMap { page -> page.resourceDescriptions().map { it.identifier() } }
            }
        }.map { DynamicResource(typeName, it) }

    fun listResources(resourceType: String): Resource<List<DynamicResource>> = listResources(CloudFormationResourceType(resourceType))

    fun getResourceDisplayName(identifier: String): String =
        if (identifier.startsWith("arn:")) {
            Arn.fromString(identifier).resourceAsString()
        } else {
            identifier
        }

    fun getResourceSchema(resourceType: CloudFormationResourceType): Resource.Cached<VirtualFile> =
        ClientBackedCachedResource(CloudFormationClient::class, "cloudformation.dynamic.resources.schema.$resourceType") {
            val schema = this.describeType {
                it.type(RegistryType.RESOURCE)
                it.typeName(resourceType.fullName)
            }.schema()
            LightVirtualFile("${resourceType}Schema.json", schema)
        }

    fun listTypes(): Resource.Cached<List<CloudFormationResourceType>> = ClientBackedCachedResource(CloudFormationClient::class, "cloudformation.listTypes") {
        this.listTypesPaginator {
            it.visibility(Visibility.PUBLIC)
            it.type(RegistryType.RESOURCE)
        }.flatMap { it.typeSummaries().map { CloudFormationResourceType(it.typeName()) } }
    }
}

data class ResourceDetails(val operations: List<PermittedOperation>, val arnRegex: String?, val documentation: String?)

enum class PermittedOperation {
    CREATE, READ, UPDATE, DELETE, LIST;
}
