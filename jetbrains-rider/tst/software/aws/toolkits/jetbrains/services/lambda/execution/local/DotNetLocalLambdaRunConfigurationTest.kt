// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.execution.local

import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.rider.test.asserts.shouldNotBeNull
import org.testng.annotations.Test
import software.aws.toolkits.jetbrains.settings.SamSettings

class DotNetLocalLambdaRunConfigurationTest : LambdaRunConfigurationTestBase() {

    override fun getSolutionDirectoryName(): String = "SamHelloWorldApp"

    @Test
    fun testHandler_ValidHandler() {
        preWarmSamVersionCache(SamSettings.getInstance().executablePath)
        preWarmLambdaHandlerValidation(handler = defaultHandler)

        runInEdtAndWait {
            val runConfiguration = createHandlerBasedRunConfiguration(
                handler = defaultHandler
            )
            runConfiguration.shouldNotBeNull()
            runConfiguration.checkConfiguration()
        }
    }

    @Test(
        expectedExceptions = [ RuntimeConfigurationError::class ],
        expectedExceptionsMessageRegExp = "Cannot find handler 'HelloWorld::HelloWorld.Function::HandlerDoesNoteExist' in project.")
    fun testHandler_NonExistingMethodName() {
        val nonExistingHandler = "HelloWorld::HelloWorld.Function::HandlerDoesNoteExist"
        preWarmSamVersionCache(SamSettings.getInstance().executablePath)
        preWarmLambdaHandlerValidation(handler = nonExistingHandler)

        runInEdtAndWait {
            val runConfiguration = createHandlerBasedRunConfiguration(
                handler = nonExistingHandler
            )
            runConfiguration.shouldNotBeNull()
            runConfiguration.checkConfiguration()
        }
    }

    @Test(
        expectedExceptions = [ RuntimeConfigurationError::class ],
        expectedExceptionsMessageRegExp = "Cannot find handler 'HelloWorld::HelloWorld.UnknownFunction::FunctionHandler' in project.")
    fun testHandler_NonExistingTypeName() {
        val nonExistingHandler = "HelloWorld::HelloWorld.UnknownFunction::FunctionHandler"
        preWarmSamVersionCache(SamSettings.getInstance().executablePath)
        preWarmLambdaHandlerValidation(handler = nonExistingHandler)

        runInEdtAndWait {
            val runConfiguration = createHandlerBasedRunConfiguration(
                handler = nonExistingHandler
            )
            runConfiguration.shouldNotBeNull()
            runConfiguration.checkConfiguration()
        }
    }

    @Test(
        expectedExceptions = [ RuntimeConfigurationError::class ],
        expectedExceptionsMessageRegExp = "Cannot find handler 'Fake' in project.")
    fun testHandler_InvalidHandlerString() {
        val invalidHandler = "Fake"
        preWarmSamVersionCache(SamSettings.getInstance().executablePath)
        preWarmLambdaHandlerValidation(handler = invalidHandler)

        runInEdtAndWait {
            val runConfiguration = createHandlerBasedRunConfiguration(
                handler = invalidHandler
            )
            runConfiguration.shouldNotBeNull()
            runConfiguration.checkConfiguration()
        }
    }

    @Test(
        expectedExceptions = [ RuntimeConfigurationError::class ],
        expectedExceptionsMessageRegExp = "Must specify a handler.")
    fun testHandler_HandlerNotSet() {
        preWarmSamVersionCache(SamSettings.getInstance().executablePath)
        preWarmLambdaHandlerValidation()

        runInEdtAndWait {
            val runConfiguration = createHandlerBasedRunConfiguration(
                handler = null
            )
            runConfiguration.shouldNotBeNull()
            runConfiguration.checkConfiguration()
        }
    }
}
