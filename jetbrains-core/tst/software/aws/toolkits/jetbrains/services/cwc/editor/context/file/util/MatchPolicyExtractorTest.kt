// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.editor.context.file.util

import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import software.aws.toolkits.jetbrains.services.amazonq.webview.FqnWebviewAdapter
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.MatchPolicy

class MatchPolicyExtractorTest {

    private lateinit var fqnWebviewAdapter: FqnWebviewAdapter

    @Before
    fun setUp() {
        fqnWebviewAdapter = mockk()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Match policy is extracted from the current file`(): Unit =
        runBlocking {
            // Stub the readImports method to return a specific string
            val importsString = "[\"java.util.List\", \"java.util.ArrayList\"]"
            coEvery { fqnWebviewAdapter.readImports(any()) } returns importsString

            val matchPolicy = MatchPolicyExtractor.extractMatchPolicyFromCurrentFile(
                isCodeSelected = false,
                fileLanguage = "java",
                fileText = "public class Test {}",
                fqnWebviewAdapter = fqnWebviewAdapter,
            )

            val targetMatchPolicy = MatchPolicy(should = setOf("java", "java.util.List", "java.util.ArrayList"))
            assertThat(matchPolicy).isEqualTo(targetMatchPolicy)
        }


    @Test
    fun `No match policy extracted if no imports in file`() =
        runBlocking {
            // Stub the readImports method to return an empty string
            val importsString = ""
            coEvery { fqnWebviewAdapter.readImports(any()) } returns importsString

            val matchPolicy = MatchPolicyExtractor.extractMatchPolicyFromCurrentFile(
                isCodeSelected = false,
                fileLanguage = "java",
                fileText = "public class Test {}",
                fqnWebviewAdapter = fqnWebviewAdapter,
            )

            assertThat(matchPolicy).isNull()
        }


    @Test
    fun `Match policy with selected code is extracted from the current file`(): Unit =
        runBlocking {
            // Stub the readImports method to return a specific string
            val importsString = "[\"java.util.List\", \"java.util.ArrayList\"]"
            coEvery { fqnWebviewAdapter.readImports(any()) } returns importsString

            val matchPolicy = MatchPolicyExtractor.extractMatchPolicyFromCurrentFile(
                isCodeSelected = true,
                fileLanguage = "java",
                fileText = "public class Test {}",
                fqnWebviewAdapter = fqnWebviewAdapter,
            )

            val targetMatchPolicy = MatchPolicy(must = setOf("java"), should = setOf("java.util.List", "java.util.ArrayList"))
            assertThat(matchPolicy).isEqualTo(targetMatchPolicy)
        }

}
