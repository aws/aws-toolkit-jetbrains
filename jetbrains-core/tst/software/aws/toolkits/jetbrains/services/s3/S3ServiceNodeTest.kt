// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3

import com.intellij.testFramework.ProjectRule
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.stub
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.http.SdkHttpResponse
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerEmptyNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerErrorNode
import software.aws.toolkits.jetbrains.utils.delegateMock
import java.time.Instant

class S3ServiceNodeTest {

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val mockClientManagerRule = MockClientManagerRule(projectRule)

    private val mockClient: S3Client by lazy { mockClientManagerRule.register(S3Client::class, delegateMock()) }

    @Test
    fun s3BucketsSortedAlphabetically() {
        createMocks()
        mockClient.stub {
            on { listBuckets(any<ListBucketsRequest>()) } doReturn ListBucketsResponse.builder()
                .apply { this.buckets(bucketData("BBB"),
                    bucketData("AAA"),
                    bucketData("ZZZ")) }.build()
        }
        val children = S3ServiceNode(projectRule.project).children

        assertThat(children).allMatch { it is S3BucketNode }
        assertThat(children.map { it.displayName() }).containsExactly("AAA", "BBB", "ZZZ")
    }

    @Test
    fun noBucketsInTheRegion() {
        createMocks()
        mockClient.stub { on {
            listBuckets(any<ListBucketsRequest>()) } doReturn ListBucketsResponse.builder().build() }
        val children = S3ServiceNode(projectRule.project).children
        assertThat(children).allMatch{it is AwsExplorerEmptyNode}
    }

    @Test
    fun errorLoadingBuckets() {
        createMocks()
        val children = S3ServiceNode(projectRule.project).children
        assertThat(children).allMatch{it is AwsExplorerErrorNode}
    }

    private fun bucketData(bucketName: String) =
            Bucket.builder()
                    .creationDate(Instant.parse("1995-10-23T10:12:35Z"))
                    .name(bucketName)
                    .build()

    private fun createMocks(){
        val mockSdkResponse = mock<SdkHttpResponse>()
        mockSdkResponse.stub {
            on { headers() } doReturn mapOf("x-amz-bucket-region" to listOf("us-east-1"))
        }

        mockClient.stub {
            on { headBucket(any<HeadBucketRequest>()) } doReturn HeadBucketResponse.builder()
                .sdkHttpResponse(mockSdkResponse).build() as HeadBucketResponse }
    }
}
