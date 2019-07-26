// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.s3.S3Client
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.services.s3.BucketActions.CopyBucketARN
import software.aws.toolkits.jetbrains.services.s3.BucketActions.CopyBucketName
import software.aws.toolkits.jetbrains.utils.delegateMock
import java.awt.datatransfer.DataFlavor
import java.time.Instant

class CopyBucketTest {

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val mockClientManagerRule = MockClientManagerRule(projectRule)
    private val s3Mock: S3Client by lazy { mockClientManagerRule.register(S3Client::class, delegateMock()) }

    @Test
    fun copyBucketName() {

        val bucket = S3BucketNode(projectRule.project, S3Bucket("foo", s3Mock, Instant.parse("1995-10-23T10:12:35Z")))
        val copy = CopyBucketName()
        copy.performCopy(bucket)
        val content = CopyPasteManager.getInstance().contents
        Assertions.assertThat(content!!.getTransferData(DataFlavor.stringFlavor)).isEqualTo("foo")
    }

    @Test
    fun copyBucketArn() {

        val bucket = S3BucketNode(projectRule.project, S3Bucket("foo", s3Mock, Instant.parse("1995-10-23T10:12:35Z")))
        val copy = CopyBucketARN()
        copy.performCopy(bucket)
        val content = CopyPasteManager.getInstance().contents
        Assertions.assertThat(content!!.getTransferData(DataFlavor.stringFlavor)).isEqualTo("arn:aws:s3:::foo")
    }
}