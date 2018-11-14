// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.upload

import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.runInEdtAndWait
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.utils.rules.JavaCodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.utils.rules.openFile
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CreateLambdaFunctionTest {
    @Rule
    @JvmField
    val projectRule = JavaCodeInsightTestFixtureRule()

    @Before
    fun setup() {
        val fixture = projectRule.fixture

        fixture.openFile("template.yaml", """
Resources:
  ServerlessFunction:
    Type: AWS::Serverless::Function
    Properties:
      CodeUri: foo
      Handler: helloworld.App::handleRequest
      Runtime: foo
      Timeout: 800
""")
    }

    @Test
    fun CreateElementBasedLambdaFunction_NonSamFunction() {
        runInEdtAndWait {
            val handlerName = "helloworld.App2"
            val action = CreateElementBasedLambdaFunction(handlerName, projectRule.project)

            val actionEvent = TestActionEvent()
            action.update(actionEvent)

            assertTrue { actionEvent.presentation.isEnabled }
            assertTrue { actionEvent.presentation.isVisible }
        }
    }

    @Test
    fun CreateElementBasedLambdaFunction_SamFunction() {
        runInEdtAndWait {
            val handlerName = "helloworld.App"
            val action = CreateElementBasedLambdaFunction(handlerName, projectRule.project)

            val actionEvent = TestActionEvent()
            action.update(actionEvent)

            assertFalse { actionEvent.presentation.isEnabled }
            assertFalse { actionEvent.presentation.isVisible }
        }
    }

    @Test
    fun CreateLambdaFunctionFromJavaClass_SamFunction() {
        runInEdtAndWait {
            val handlerName = "helloworld.App"
            val action = CreateLambdaFunctionFromJavaClass(handlerName, projectRule.project)

            val actionEvent = TestActionEvent()
            action.update(actionEvent)

            assertFalse { actionEvent.presentation.isEnabled }
            assertFalse { actionEvent.presentation.isVisible }
        }
    }

    @Test
    fun CreateLambdaFunctionFromJavaClass_NonSamFunction() {
        runInEdtAndWait {
            val handlerName = "helloworld.App2"
            val action = CreateLambdaFunctionFromJavaClass(handlerName, projectRule.project)

            val actionEvent = TestActionEvent()
            action.update(actionEvent)

            assertTrue { actionEvent.presentation.isEnabled }
            assertTrue { actionEvent.presentation.isVisible }
        }
    }

    @Test
    fun CreateLambdaFunctionFromJavaClass_NonSamFunction_Substring() {
        runInEdtAndWait {
            val handlerName = "helloworld.Ap"
            val action = CreateLambdaFunctionFromJavaClass(handlerName, projectRule.project)

            val actionEvent = TestActionEvent()
            action.update(actionEvent)

            assertTrue { actionEvent.presentation.isEnabled }
            assertTrue { actionEvent.presentation.isVisible }
        }
    }

    @Test
    fun CreateLambdaFunctionFromJavaMethod_SamFunction() {
        runInEdtAndWait {
            val handlerName = "helloworld.App::handleRequest"
            val action = CreateLambdaFunctionFromJavaMethod(handlerName, projectRule.project)

            val actionEvent = TestActionEvent()
            action.update(actionEvent)

            assertFalse { actionEvent.presentation.isEnabled }
            assertFalse { actionEvent.presentation.isVisible }
        }
    }

    @Test
    fun CreateLambdaFunctionFromJavaMethod_NonSamFunction() {
        runInEdtAndWait {
            val handlerName = "helloworld.App2::handleRequest"
            val action = CreateLambdaFunctionFromJavaMethod(handlerName, projectRule.project)

            val actionEvent = TestActionEvent()
            action.update(actionEvent)

            assertTrue { actionEvent.presentation.isEnabled }
            assertTrue { actionEvent.presentation.isVisible }
        }
    }

    @Test
    fun CreateLambdaFunctionFromJavaMethod_NonSamFunction_Substring() {
        runInEdtAndWait {
            val handlerName = "helloworld.App::handleReques"
            val action = CreateLambdaFunctionFromJavaMethod(handlerName, projectRule.project)

            val actionEvent = TestActionEvent()
            action.update(actionEvent)

            assertTrue { actionEvent.presentation.isEnabled }
            assertTrue { actionEvent.presentation.isVisible }
        }
    }
}

class CreateLambdaFunctionTest_Python {
    @Rule
    @JvmField
    val projectRule = JavaCodeInsightTestFixtureRule()

    @Before
    fun setup() {
        val fixture = projectRule.fixture

        fixture.openFile("template.yaml", """
Resources:
  ServerlessFunction:
    Type: AWS::Serverless::Function
    Properties:
      CodeUri: foo
      Handler: hello_world.app.lambda_handler
      Runtime: foo
      Timeout: 800
""")
    }

    @Test
    fun CreateLambdaFunctionFromPythonMethod_SamFunction() {
        runInEdtAndWait {
            val handlerName = "hello_world.app.lambda_handler"
            val action = CreateLambdaFunctionFromPythonMethod(handlerName, projectRule.project)

            val actionEvent = TestActionEvent()
            action.update(actionEvent)

            assertFalse { actionEvent.presentation.isEnabled }
            assertFalse { actionEvent.presentation.isVisible }
        }
    }

    @Test
    fun CreateLambdaFunctionFromPythonMethod_NonSamFunction() {
        runInEdtAndWait {
            val handlerName = "hello_world.app2.lambda_handler"
            val action = CreateLambdaFunctionFromPythonMethod(handlerName, projectRule.project)

            val actionEvent = TestActionEvent()
            action.update(actionEvent)

            assertTrue { actionEvent.presentation.isEnabled }
            assertTrue { actionEvent.presentation.isVisible }
        }
    }

    @Test
    fun CreateLambdaFunctionFromPythonMethod_NonSamFunction_Substring() {
        runInEdtAndWait {
            val handlerName = "hello_world.app.lambda_handle"
            val action = CreateLambdaFunctionFromPythonMethod(handlerName, projectRule.project)

            val actionEvent = TestActionEvent()
            action.update(actionEvent)

            assertTrue { actionEvent.presentation.isEnabled }
            assertTrue { actionEvent.presentation.isVisible }
        }
    }
}
