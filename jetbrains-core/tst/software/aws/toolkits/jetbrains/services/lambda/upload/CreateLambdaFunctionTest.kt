// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.upload

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndWait
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.mock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.services.lambda.LambdaHandlerResolver
import software.aws.toolkits.jetbrains.utils.rules.JavaCodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.utils.rules.openFile
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CreateLambdaFunctionTest {
    @Rule
    @JvmField
    val projectRule = JavaCodeInsightTestFixtureRule()

    lateinit var smartElement: SmartPsiElementPointer<PsiElement>

    @Before
    fun setup() {
        val fixture = projectRule.fixture

        val element = fixture.addClass("""
public class Processor {
    public void handler() {

    }
}
        """).findMethodsByName("handler", false).first()

        runInEdtAndWait {
            smartElement = SmartPointerManager.createPointer(element)
        }
    }

    @Test
    fun InvalidNullArgs() {
        val handlerName = "helloworld.App::handleRequest"

        openJavaRuntimeTemplate(projectRule.fixture)
        runInEdtAndWait {
            assertFails { CreateLambdaFunction(handlerName, null, null) }
        }
    }

    @Test
    fun InvalidNullArgs_Element() {
        val handlerName = "helloworld.App::handleRequest"
        val handlerResolver = mockLambdaHandlerResolverWithHandler(handlerName)

        openJavaRuntimeTemplate(projectRule.fixture)
        runInEdtAndWait {
            assertFails { CreateLambdaFunction(handlerName, null, handlerResolver) }
        }
    }

    @Test
    fun InvalidNullArgs_HandlerResolver() {
        val handlerName = "helloworld.App::handleRequest"

        openJavaRuntimeTemplate(projectRule.fixture)
        runInEdtAndWait {
            assertFails { CreateLambdaFunction(handlerName, smartElement, null) }
        }
    }

    @Test
    fun SamFunction_javaRuntime() {
        val handlerName = "helloworld.App::handleRequest"
        val handlerResolver = mockLambdaHandlerResolverWithHandler(handlerName)

        openJavaRuntimeTemplate(projectRule.fixture)
        runInEdtAndWait {
            val action = CreateLambdaFunction(handlerName, smartElement, handlerResolver)

            val actionEvent = TestActionEvent()
            action.update(actionEvent)

            assertTrue { actionEvent.presentation.isVisible }
        }
    }

    @Test
    fun SamFunction_pythonRuntime() {
        val handlerName = "helloworld.App::handleRequest"
        val handlerResolver = mockLambdaHandlerResolverWithHandler(handlerName)

        openPythonRuntimeTemplate(projectRule.fixture)
        runInEdtAndWait {
            val action = CreateLambdaFunction(handlerName, smartElement, handlerResolver)

            val actionEvent = TestActionEvent()
            action.update(actionEvent)

            assertFalse { actionEvent.presentation.isVisible }
        }
    }

    @Test
    fun NonSamFunction_javaRuntime() {
        val handlerName = "helloworld.App2::handleRequest"
        val handlerResolver = mockLambdaHandlerResolverWithHandler(handlerName)

        openJavaRuntimeTemplate(projectRule.fixture)
        runInEdtAndWait {
            val action = CreateLambdaFunction(handlerName, smartElement, handlerResolver)

            val actionEvent = TestActionEvent()
            action.update(actionEvent)

            assertTrue { actionEvent.presentation.isVisible }
        }
    }

    @Test
    fun NonSamFunction_pythonRuntime() {
        val handlerName = "helloworld.App2::handleRequest"
        val handlerResolver = mockLambdaHandlerResolverWithHandler(handlerName)

        openPythonRuntimeTemplate(projectRule.fixture)
        runInEdtAndWait {
            val action = CreateLambdaFunction(handlerName, smartElement, handlerResolver)

            val actionEvent = TestActionEvent()
            action.update(actionEvent)

            assertTrue { actionEvent.presentation.isVisible }
        }
    }

    @Test
    fun NonSamFunction_Substring() {
        val handlerName = "helloworld.App::handleReques"
        val handlerResolver = mockLambdaHandlerResolverWithHandler(handlerName)

        openJavaRuntimeTemplate(projectRule.fixture)
        runInEdtAndWait {
            val action = CreateLambdaFunction(handlerName, smartElement, handlerResolver)

            val actionEvent = TestActionEvent()
            action.update(actionEvent)

            assertTrue { actionEvent.presentation.isVisible }
        }
    }

    private fun mockLambdaHandlerResolverWithHandler(handler: String): LambdaHandlerResolver {
        val handler1 = argumentCaptor<String>()
        val handler2 = argumentCaptor<String>()
        return mock<LambdaHandlerResolver> {
            on { determineHandler(any()) }.doAnswer { handler }
            on { areHandlersEquivalent(handler1.capture(), handler2.capture()) }.doAnswer { handler1.firstValue == handler2.firstValue }
        }
    }

    private fun openJavaRuntimeTemplate(fixture: JavaCodeInsightTestFixture) {
        fixture.openFile("template.yaml", """
Resources:
  ServerlessFunction:
    Type: AWS::Serverless::Function
    Properties:
      CodeUri: foo
      Handler: helloworld.App::handleRequest
      Runtime: java8
""")
    }

    private fun openPythonRuntimeTemplate(fixture: JavaCodeInsightTestFixture) {
        fixture.openFile("template.yaml", """
Resources:
  ServerlessFunction:
    Type: AWS::Serverless::Function
    Properties:
      CodeUri: foo
      Handler: helloworld.App::handleRequest
      Runtime: python2.7
""")
    }
}