// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.s3.objectActions

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.Bucket
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.core.credentials.MockAwsConnectionManager
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeDirectoryNode
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeObjectNode
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeObjectVersionNode
import software.aws.toolkits.jetbrains.services.s3.editor.S3VirtualBucket
import java.awt.datatransfer.DataFlavor
import java.time.Instant

class CopyUrlActionTest : ObjectActionTestBase() {
    @Rule
    @JvmField
    val settingsManagerRule = MockAwsConnectionManager.ProjectAccountSettingsManagerRule(projectRule)

    @Rule
    @JvmField
    val disposableRule = DisposableRule()

    private val sut = CopyUrlAction()

    @Test
    fun `copy url disabled with no nodes`() {
        assertThat(sut.updateAction(emptyList()).isEnabled).isFalse
    }

    @Test
    fun `copy url disabled with on multiple nodes`() {
        val nodes = listOf(
            S3TreeDirectoryNode(s3Bucket(), null, "path1/"),
            S3TreeDirectoryNode(s3Bucket(), null, "path2/")
        )
        assertThat(sut.updateAction(nodes).isEnabled).isFalse
    }

    @Test
    fun `copy url enabled with on single node`() {
        val nodes = listOf(
            S3TreeDirectoryNode(s3Bucket(), null, "path1/"),
        )
        assertThat(sut.updateAction(nodes).isEnabled).isTrue
    }

    @Test
    fun `copy url enabled with on version nodes`() {
        val dir = S3TreeDirectoryNode(s3Bucket(), null, "path1/")
        val obj = S3TreeObjectNode(dir, "path1/obj1", 1, Instant.now())
        val nodes = listOf(
            S3TreeObjectVersionNode(obj, "version", 1, Instant.now())
        )

        assertThat(sut.updateAction(nodes).isEnabled).isTrue
    }

    @Test
    fun `copy url for directory is correct`() {
        val nodes = listOf(
            S3TreeDirectoryNode(s3Bucket(), null, "path1/"),
        )
        sut.executeAction(nodes)

        val data = CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)
        assertThat(data).isEqualTo("https://$bucketName.s3.amazonaws.com/path1/")
    }

    @Test
    fun `copy url for object is correct`() {
        val dir = S3TreeDirectoryNode(s3Bucket(), null, "path1/")
        val nodes = listOf(
            S3TreeObjectNode(dir, "path1/obj1", 1, Instant.now())
        )
        sut.executeAction(nodes)

        val data = CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)
        assertThat(data).isEqualTo("https://$bucketName.s3.amazonaws.com/path1/obj1")
    }

    @Test
    fun `copy url for obj version value is correct`() {
        val dir = S3TreeDirectoryNode(s3Bucket(), null, "path1/")
        val obj = S3TreeObjectNode(dir, "path1/obj1", 1, Instant.now())
        val nodes = listOf(
            S3TreeObjectVersionNode(obj, "version", 1, Instant.now())
        )
        sut.executeAction(nodes)

        val data = CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)
        assertThat(data).isEqualTo("https://$bucketName.s3.amazonaws.com/path1/obj1?versionId=version")
    }

    override fun s3Bucket(): S3VirtualBucket {
        val awsConnectionManager = settingsManagerRule.settingsManager

        // Use real manager for this since it can affect the S3Configuration that goes into S3Utilities
        val s3Client = AwsClientManager().getClient<S3Client>(awsConnectionManager.activeCredentialProvider, awsConnectionManager.activeRegion)
        Disposer.register(disposableRule.disposable, { s3Client.close() })

        return S3VirtualBucket(Bucket.builder().name(bucketName).build(), s3Client)
    }
}
