// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0package software.aws.toolkits.jetbrains.services.s3.BucketActions
package software.aws.toolkits.jetbrains.services.s3.BucketActions

import software.amazon.awssdk.services.s3.S3Client
import software.aws.toolkits.core.s3.deleteBucketAndContents
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.core.DeleteResourceAction
import software.aws.toolkits.jetbrains.services.s3.S3BucketNode
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService

class DeleteBucketAction : DeleteResourceAction<S3BucketNode>("Delete S3 Bucket") {

    override fun performDelete(selected: S3BucketNode) {
        val client: S3Client = AwsClientManager.getInstance(selected.nodeProject).getClient()
        client.deleteBucketAndContents(selected.toString())
        TelemetryService.getInstance().record(selected.nodeProject, "s3") {
            datum("deletebucket") {
                count()
            }
        }
    }
}