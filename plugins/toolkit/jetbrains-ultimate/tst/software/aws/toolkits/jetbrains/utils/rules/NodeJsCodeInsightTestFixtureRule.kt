// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils.rules

import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.javascript.nodejs.interpreter.local.NodeJsLocalInterpreter
import com.intellij.javascript.nodejs.interpreter.local.NodeJsLocalInterpreterManager
import com.intellij.lang.javascript.dialects.JSLanguageLevel
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.lang.javascript.settings.JSRootConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.WebModuleTypeBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.text.SemVer
import com.intellij.xdebugger.XDebuggerUtil
import org.intellij.lang.annotations.Language
import org.junit.Assume
import software.aws.toolkit.jetbrains.utils.rules.CodeInsightTestFixtureRule

/**
 * JUnit test Rule that will create a Light [Project] and [CodeInsightTestFixture] with NodeJs support. Projects are
 * lazily created and are torn down after each test.
 *
 * If you wish to have just a [Project], you may use Intellij's [com.intellij.testFramework.ProjectRule]
 */
class NodeJsCodeInsightTestFixtureRule : CodeInsightTestFixtureRule(NodeJsLightProjectDescriptor()) {
    override fun createTestFixture(): CodeInsightTestFixture {
        val codeInsightFixture = super.createTestFixture()
        // JavaScript plugin services may not be available in newer IDE test environments (2026.1+)
        Assume.assumeTrue(
            "NodeJs plugin services not available in test environment",
            ApplicationManager.getApplication().getServiceIfCreated(NodeJsLocalInterpreterManager::class.java) != null
        )
        PsiTestUtil.addContentRoot(codeInsightFixture.module, codeInsightFixture.tempDirFixture.getFile(".")!!)
        codeInsightFixture.project.setNodeJsInterpreterVersion(SemVer("v8.10.10", 8, 10, 10))
        // JSRootConfiguration may not be available in newer IDE test environments (2026.1+)
        try {
            codeInsightFixture.project.setJsLanguageLevel(JSLanguageLevel.ES6)
        } catch (_: Exception) {
        }

        return codeInsightFixture
    }

    fun addBreakpoint() {
        runInEdtAndWait {
            val document = fixture.editor.document
            val psiFile = fixture.file as JSFile
            val lineNumber = document.getLineNumber(psiFile.statements.first().textOffset)

            XDebuggerUtil.getInstance().toggleLineBreakpoint(
                project,
                fixture.file.virtualFile,
                lineNumber
            )
        }
    }
}

class NodeJsLightProjectDescriptor : LightProjectDescriptor() {
    override fun getSdk(): Sdk? = null

    override fun getModuleTypeId(): String =
        try {
            // In 2026.1+, WebModuleTypeBase may not be registered in the test environment,
            // causing UnknownModuleType ClassCastException during test setup
            WebModuleTypeBase.getInstance().id
        } catch (_: Exception) {
            super.getModuleTypeId()
        }
}

class MockNodeJsInterpreter(private var version: SemVer) : NodeJsLocalInterpreter("/path/to/$version/mock/node") {
    init {
        // NodeJsLocalInterpreterManager may not be available in newer IDE test environments (2026.1+)
        try {
            NodeJsLocalInterpreterManager.getInstance().interpreters =
                NodeJsLocalInterpreterManager.getInstance().interpreters + listOf(this)
        } catch (_: Exception) {
        }
    }

    // could differ on windows causing interpreter lookup failure during tests
    override fun getPresentableName(): String = referenceName

    override fun getCachedVersion(): Ref<SemVer> = Ref(version)
}

class HeavyNodeJsCodeInsightTestFixtureRule : CodeInsightTestFixtureRule() {
    override fun createTestFixture(): CodeInsightTestFixture {
        val fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory()
        val projectFixture = fixtureFactory.createFixtureBuilder(testName)
        val codeInsightFixture = fixtureFactory.createCodeInsightFixture(projectFixture.fixture)
        codeInsightFixture.setUp()
        codeInsightFixture.testDataPath = testDataPath

        return codeInsightFixture
    }

    fun addBreakpoint() {
        runInEdtAndWait {
            val document = fixture.editor.document
            val psiFile = fixture.file as JSFile
            val lineNumber = document.getLineNumber(psiFile.statements.first().textOffset)

            XDebuggerUtil.getInstance().toggleLineBreakpoint(
                project,
                fixture.file.virtualFile,
                lineNumber
            )
        }
    }
}

fun Project.setNodeJsInterpreterVersion(version: SemVer) {
    NodeJsInterpreterManager.getInstance(this).setInterpreterRef(
        NodeJsInterpreterRef.create(MockNodeJsInterpreter(version))
    )
}

fun Project.setJsLanguageLevel(languageLevel: JSLanguageLevel) {
    JSRootConfiguration.getInstance(this)
        .storeLanguageLevelAndUpdateCaches(languageLevel)
}

fun CodeInsightTestFixture.addLambdaHandler(
    subPath: String = ".",
    fileName: String = "app",
    handlerName: String = "lambdaHandler",
    @Language("JS") fileContent: String =
        """
        exports.$handlerName = function (event, context, callback) {
            return 'HelloWorld'
        };
        """.trimIndent(),
): PsiElement {
    val psiFile = this.addFileToProject("$subPath/$fileName.js", fileContent) as JSFile

    return runInEdtAndGet {
        psiFile.findElementAt(fileContent.indexOf(handlerName))!!
    }
}

fun CodeInsightTestFixture.addTypeScriptLambdaHandler(
    subPath: String = ".",
    fileName: String = "app",
    handlerName: String = "lambdaHandler",
    @Language("TS") fileContent: String =
        """
        export const $handlerName = (event: APIGatewayProxyEvent, context: Context, callback: Callback<APIGatewayProxyResult>): APIGatewayProxyResult => {
            return { statusCode: 200 }
        }
        """.trimIndent(),
): PsiElement {
    val psiFile = this.addFileToProject("$subPath/$fileName.ts", fileContent) as JSFile

    return runInEdtAndGet {
        psiFile.findElementAt(fileContent.indexOf(handlerName))!!
    }
}

fun CodeInsightTestFixture.addPackageJsonFile(
    subPath: String = ".",
    @Language("JSON") content: String =
        """
        {
            "name": "hello-world",
            "version": "1.0.0"
        }
        """.trimIndent(),
): PsiFile = this.addFileToProject("$subPath/package.json", content)

fun CodeInsightTestFixture.addTypeScriptPackageJsonFile(
    subPath: String = ".",
    @Language("JSON") content: String =
        """
        {
            "name": "hello-world",
            "version": "1.0.0",
            "devDependencies": {
              "typescript": "*"
            }
        }
        """.trimIndent(),
): PsiFile = this.addPackageJsonFile(subPath, content)
