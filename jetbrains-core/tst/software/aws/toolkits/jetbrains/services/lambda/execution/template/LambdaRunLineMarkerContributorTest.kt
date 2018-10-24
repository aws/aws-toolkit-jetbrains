// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.execution.template

import assertk.assert
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndWait
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.testutils.rules.JavaCodeInsightTestFixtureRule

class LambdaRunLineMarkerContributorTest {

    @Rule
    @JvmField
    val projectRule = JavaCodeInsightTestFixtureRule()

    @Test
    fun testServerlessFunctionIsMarked() {
        projectRule.fixture.openFile("template.yaml", """
Resources:
  ServerlessFunction:
    Type: AWS::Serverless::Function
    Properties:
      CodeUri: target/HelloWorld-1.0.jar
      Handler: helloworld.ConcreteClass::handleRequest
      Runtime: java8
""")

        runAndAssertionMarks(projectRule.fixture) { marks ->
            assert(marks).hasSize(1)
            assert(marks.first().lineMarkerInfo.element).isNotNull {
                it.actual.text == "ServerlessFunction"
            }
        }
    }

    @Test
    fun testLambdaFunctionIsMarked() {
        projectRule.fixture.openFile("template.yaml", """
Resources:
  LambdaFunction:
    Type: AWS::Lambda::Function
    Properties:
      CodeUri: target/HelloWorld-1.0.jar
      Handler: helloworld.ConcreteClass::handleRequest
      Runtime: java8
""")
        runAndAssertionMarks(projectRule.fixture) { marks ->
            assert(marks).hasSize(1)
            assert(marks.first().lineMarkerInfo.element).isNotNull {
                assert(it.actual.text).isEqualTo("LambdaFunction")
            }
        }
    }

    @Test
    fun testEmptyMarks() {
        projectRule.fixture.openFile("template.yaml", """
Resources:
  FooApi:
    Type: AWS::Serverless::Api
    Properties:
      StageName: prod
      DefinitionUri: swagger.yml
""")
        runAndAssertionMarks(projectRule.fixture) { marks ->
            assert(marks).isEmpty()
        }
    }

    private fun runAndAssertionMarks(fixture: CodeInsightTestFixture, assertion: (List<LineMarkerInfo.LineMarkerGutterIconRenderer<PsiElement>>) -> Unit) {
        runInEdtAndWait {
            val marks = fixture.findAllGutters().filterIsInstance<LineMarkerInfo.LineMarkerGutterIconRenderer<PsiElement>>()
            assertion(marks)
        }
    }

    private fun CodeInsightTestFixture.openFile(relativePath: String, fileText: String): VirtualFile {
        val file = this.addFileToProject(relativePath, fileText).virtualFile
        runInEdtAndWait {
            this.openFileInEditor(file)
        }

        return file
    }
}