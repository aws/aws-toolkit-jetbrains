// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.s3.bucketActions

import software.aws.toolkits.jetbrains.core.explorer.actions.ViewResourceAction
import software.aws.toolkits.jetbrains.services.s3.S3ServiceNode
import software.aws.toolkits.jetbrains.services.s3.openEditor
import software.amazon.awssdk.services.s3.model.Bucket

class ViewBucketAction : ViewResourceAction<S3ServiceNode>("View Bucket", "S3 Bucket") {

    override fun viewResource(resourceToView: String, selected: S3ServiceNode) {
        val bucket = Bucket.builder().name(resourceToView).build()
        openEditor(selected.nodeProject,bucket)
    }
}
