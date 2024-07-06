// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.workspace.context

import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import org.apache.commons.codec.digest.DigestUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.services.cwc.editor.context.project.EncoderServer
import software.aws.toolkits.jetbrains.utils.rules.CodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.utils.rules.JavaCodeInsightTestFixtureRule

class EncoderServerTest {
    @Rule @JvmField
    val projectRule: CodeInsightTestFixtureRule = JavaCodeInsightTestFixtureRule()
    private val testUrl = "https://amazon.com"
    internal lateinit var project: Project
    private lateinit var encoderServer: EncoderServer

    @Before
    open fun setup() {
        project = projectRule.project
        encoderServer = EncoderServer(project)
    }

    @Test
    fun `test download artifacts validate hash if it does not match`() {
        val inputBytes = HttpRequests.request(testUrl).readBytes(null)
        val wrongHash = "sha384:ad527e9583d3dc4be3d302bac17f8d5a64eb8f5ab536717982620232e4e4bad82d1041fb73ae27899e9e802f07f61567"

        val actual = encoderServer.validateHash(wrongHash, inputBytes)
        assertThat(actual).isEqualTo(false)
    }

    @Test
    fun `test download artifacts validate hash if it matches`() {
        val inputBytes = HttpRequests.request(testUrl).readBytes(null)
        val rightHash = "sha384:${DigestUtils.sha384Hex(inputBytes)}"

        val actual = encoderServer.validateHash(rightHash, inputBytes)
        assertThat(actual).isEqualTo(true)
    }
}
