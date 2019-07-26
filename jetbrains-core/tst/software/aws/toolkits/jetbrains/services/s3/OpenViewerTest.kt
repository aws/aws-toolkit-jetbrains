// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3

// import com.intellij.openapi.actionSystem.DataContext
// import com.intellij.openapi.editor.Editor
// import com.intellij.openapi.fileEditor.EditorDataProvider
// import com.intellij.openapi.fileEditor.FileEditorManager
// import com.intellij.openapi.fileEditor.OpenFileDescriptor
// import com.intellij.openapi.ide.CopyPasteManager
// import com.intellij.testFramework.*
// import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
// import com.intellij.testFramework.fixtures.CodeInsightTestFixture
// import com.nhaarman.mockitokotlin2.*
// import org.assertj.core.api.Assertions
// import org.junit.Rule
// import org.junit.Test
// import software.amazon.awssdk.core.sync.RequestBody
// import software.amazon.awssdk.regions.Region
// import software.amazon.awssdk.services.s3.S3Client
// import software.amazon.awssdk.services.s3.model.BucketVersioningStatus
// import software.amazon.awssdk.services.s3.model.PutObjectRequest
// import software.amazon.awssdk.services.s3.model.S3Exception
// import software.aws.toolkits.core.rules.S3TemporaryBucketRule
// import software.aws.toolkits.core.s3.deleteBucketAndContents
// import software.aws.toolkits.jetbrains.core.MockClientManagerRule
// import software.aws.toolkits.jetbrains.services.s3.BucketActions.CopyBucketName
// import software.aws.toolkits.jetbrains.services.s3.BucketActions.OpenBucketViewerAction
// import software.aws.toolkits.jetbrains.services.s3.BucketActions.S3BucketViewer
// import software.aws.toolkits.jetbrains.utils.delegateMock
// import java.awt.datatransfer.DataFlavor
// import java.time.Instant
// import org.assertj.core.api.Assertions.assertThat
// import software.aws.toolkits.jetbrains.utils.rules.CodeInsightTestFixtureRule
// import software.aws.toolkits.jetbrains.utils.rules.JavaCodeInsightTestFixtureRule
//
// class OpenViewerTest {

//    @Rule
//    @JvmField
//    val projectRule = CodeInsightTestFixtureRule()
//
//    @Rule
//    @JvmField
//    val mockClientManager = MockClientManagerRule { projectRule.project }
//    private val s3Mock: S3Client by lazy { mockClientManager.register(S3Client::class, delegateMock()) }
//
//    @Test
//    fun openViewerSuccessful() {
//
//
//        val fileEditorManager = FileEditorManager.getInstance(projectRule.project)
//      val mockEditor = mock<Editor>()
//
//        val codeRule = projectRule.fixture
//        val editor = codeRule.editor
//
//        val mockFileEditor = mock<FileEditorManager> {
//            on { openTextEditor(any<OpenFileDescriptor>(), any()) } doReturn editor
//            val vfsMock = S3VFS(s3Mock)
//
//            val bucket = S3BucketNode(projectRule.project, S3Bucket("TestBucket", s3Mock, Instant.parse("1995-10-23T10:12:35Z")))
//
//            val virtualBucket = S3VirtualBucket(vfsMock, S3Bucket("TestBucket", s3Mock, Instant.parse("1995-10-23T10:12:35Z")))
//
//            val openTest = S3BucketViewer()
//
//            runInEdtAndWait {
//
//            openTest.openEditor(bucket, s3Mock, projectRule.project)
//
//
//
//            assertThat(fileEditorManager.openFiles).allMatch { it is S3VirtualBucket }
//
//            }
//
//                assertThat(fileEditorManager.openFiles).hasOnlyOneElementSatisfying { assertThat(it.name).isEqualTo(bucket) }
//            }
//        }
//
//    }