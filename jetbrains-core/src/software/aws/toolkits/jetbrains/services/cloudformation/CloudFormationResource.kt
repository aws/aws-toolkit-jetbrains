// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation

import software.aws.toolkits.resources.cloudformation.CloudFormationResourceType

/**
 * Marker interface used for filtering now, but in the future can also be used by CloudAPI
 */
interface CloudFormationResource {
    val resourceType: CloudFormationResourceType
    val cfnPhysicalIdentifier: String
}
