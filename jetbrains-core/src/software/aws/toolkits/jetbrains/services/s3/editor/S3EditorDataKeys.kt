// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.s3.editor

import com.intellij.openapi.actionSystem.DataKey

object S3EditorDataKeys {
    /**
     * Returns all the selected nodes. Note: Error, Continuation, and loading nodes are filtered out
     */
    val SELECTED_NODES = DataKey.create<List<S3TreeNode>>("aws.s3.bucketViewer.selectedNodes")

    /**
     * Returns the Bucket for the viewer
     */
    val BUCKET = DataKey.create<S3VirtualBucket>("aws.s3.bucketViewer.bucket")

    /**
     * Returns the S3 bucket viewer table
     */
    val BUCKET_VIEWER = DataKey.create<S3TreeTable>("aws.s3.bucketViewer")
}
