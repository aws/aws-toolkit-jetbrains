// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.completion

import com.jetbrains.rd.framework.impl.RpcTimeouts
import com.jetbrains.rider.model.ImageSourceIconModel
import com.jetbrains.rider.model.lambdaModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.base.BaseTestWithSolution
import org.assertj.core.api.Assertions.assertThat
import org.testng.annotations.Test
import software.amazon.awssdk.services.lambda.model.Runtime

class DotNetHandlerCompletionTest : BaseTestWithSolution() {

    override fun getSolutionDirectoryName(): String = ""

    override val waitForCaches = true

    @Test
    @TestEnvironment(solution = "SamHelloWorldApp")
    fun testCompletion_IsSupportedForDotNetRuntime() {
        val provider = HandlerCompletionProvider(project, Runtime.DOTNETCORE2_1)
        assertThat(provider.isCompletionSupported).isTrue()
    }

    @Test
    @TestEnvironment(solution = "SamHelloWorldApp")
    fun testDetermineHandlers_SingleHandler() {
        val handlers = project.solution.lambdaModel.determineHandlers.sync(Unit, RpcTimeouts.default)

        assertThat(handlers.size).isEqualTo(1)
        assertThat(handlers.first().handler).isEqualTo("HelloWorld::HelloWorld.Function::FunctionHandler")
        assertThat(handlers.first().iconId).isEqualTo(ImageSourceIconModel(iconPackStringId = 20, iconNameStringId = 31))
    }

    @Test
    @TestEnvironment(solution = "SamMultipleHandlersApp")
    fun testDetermineHandlers_MultipleHandlers() {
        val handlers = project.solution.lambdaModel.determineHandlers.sync(Unit, RpcTimeouts.default)

        assertThat(handlers.size).isEqualTo(2)

        assertThat(handlers[0].handler).isEqualTo("HelloWorld::HelloWorld.Function::FunctionHandler")
        assertThat(handlers[0].iconId).isEqualTo(ImageSourceIconModel(iconPackStringId = 20, iconNameStringId = 31))

        assertThat(handlers[1].handler).isEqualTo("HelloWorld::HelloWorld.Function2::FunctionHandler2")
        assertThat(handlers[1].iconId).isEqualTo(ImageSourceIconModel(iconPackStringId = 20, iconNameStringId = 31))
    }
}
