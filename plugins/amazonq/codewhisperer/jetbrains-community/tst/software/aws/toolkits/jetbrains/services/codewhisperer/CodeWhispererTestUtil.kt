// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.eclipse.lsp4j.jsonrpc.messages.Either
import software.amazon.awssdk.awscore.DefaultAwsResponseMetadata
import software.amazon.awssdk.awscore.util.AwsHeader
import software.amazon.awssdk.http.SdkHttpResponse
import software.aws.toolkits.core.utils.test.aString
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionImports
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionItem
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionListWithReferences
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionReference
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionReferencePosition
import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererProgrammingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererC
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererCpp
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererCsharp
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererGo
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererJava
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererJavaScript
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererJsx
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererKotlin
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererPhp
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererPython
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererRuby
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererScala
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererShell
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererSql
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererTypeScript
import software.aws.toolkits.jetbrains.services.codewhisperer.model.CaretContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.CaretPosition
import software.aws.toolkits.jetbrains.services.codewhisperer.model.FileContextInfo
import software.aws.toolkits.jetbrains.services.codewhisperer.model.LatencyContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.TriggerTypeInfo
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererAutomatedTriggerType
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererService
import software.aws.toolkits.jetbrains.services.codewhisperer.service.RequestContext
import software.aws.toolkits.telemetry.CodewhispererTriggerType
import java.nio.file.Paths
import kotlin.random.Random

object CodeWhispererTestUtil {
    const val testSessionId = "test_codewhisperer_session_id"
    const val testRequestId = "test_aws_request_id"
    const val testRequestIdForCodeWhispererException = "test_request_id_for_codewhispererException"
    const val codeWhispererRecommendationActionId = "CodeWhispererRecommendationAction"
    const val codeWhispererCodeScanActionId = "codewhisperer.toolbar.security.scan"
    const val testValidAccessToken = "test_valid_access_token"
    val testNextToken: Either<String, Int> = Either.forLeft("test_next_token")
    val metadata: DefaultAwsResponseMetadata = DefaultAwsResponseMetadata.create(
        mapOf(AwsHeader.AWS_REQUEST_ID to testRequestId)
    )
    val sdkHttpResponse = SdkHttpResponse.builder().headers(
        mapOf(CodeWhispererService.KET_SESSION_ID to listOf(testSessionId))
    ).build()

    val pythonResponse: InlineCompletionListWithReferences = InlineCompletionListWithReferences(
        items = listOf(
            InlineCompletionItem("item1", "(x, y):\n    return x + y"),
            InlineCompletionItem("item2", "(a, b):\n    return a + b"),
            InlineCompletionItem("item3", "test recommendation 3"),
            InlineCompletionItem("item4", "test recommendation 4"),
            InlineCompletionItem("item4", "test recommendation 5"),
        ),
        sessionId = "sessionId",
    )
    val javaResponse: InlineCompletionListWithReferences = InlineCompletionListWithReferences(
        items = listOf(
            InlineCompletionItem("item1", "(x, y) {\n        return x + y\n    }"),
            InlineCompletionItem("item2", "(a, b) {\n        return a + b\n    }"),
            InlineCompletionItem("item3", "test recommendation 3"),
            InlineCompletionItem("item4", "test recommendation 4"),
            InlineCompletionItem("item5", "test recommendation 5"),
        ),
        sessionId = "sessionId",
    )
    const val pythonFileName = "test.py"
    const val javaFileName = "test.java"
    const val cppFileName = "test.cpp"
    const val jsFileName = "test.js"
    const val pythonTestLeftContext = "def addTwoNumbers"
    const val keystrokeInput = "a"
    const val cppTestLeftContext = "int addTwoNumbers"
    const val javaTestContext = "public class Test {\n    public static void main\n}"
    const val yaml_langauge = "yaml"
    const val leftContext_success_Iac = "# Create an S3 Bucket named CodeWhisperer in CloudFormation"
    const val leftContext_failure_Iac = "Create an S3 Bucket named CodeWhisperer"
}

fun aCompletion(content: String? = null, isEmpty: Boolean = false, referenceCount: Int? = null, importCount: Int? = null): InlineCompletionItem {
    val myReferenceCount = referenceCount ?: Random.nextInt(0, 4)
    val myImportCount = importCount ?: Random.nextInt(0, 4)

    val references = List(myReferenceCount) {
        InlineCompletionReference(
            aString(),
            aString(),
            aString(),
            InlineCompletionReferencePosition()
        )
    }

    val imports = List(myImportCount) {
        InlineCompletionImports(aString())
    }

    return InlineCompletionItem(
        itemId = aString(),
        insertText = content ?: if (!isEmpty) aString() else "",
        references = references,
        mostRelevantMissingImports = imports
    )
}

fun aFileContextInfo(language: CodeWhispererProgrammingLanguage? = null): FileContextInfo {
    val caretContextInfo = CaretContext(aString(), aString(), aString())
    val fileName = aString()
    val fileRelativePath = Paths.get("test", fileName).toString()
    val programmingLanguage = language ?: listOf(
        CodeWhispererPython.INSTANCE,
        CodeWhispererJava.INSTANCE
    ).random()

    return FileContextInfo(caretContextInfo, fileName, programmingLanguage, fileRelativePath)
}

fun aTriggerType(): CodewhispererTriggerType =
    CodewhispererTriggerType.values().filterNot { it == CodewhispererTriggerType.Unknown }.random()

fun aRequestContext(
    project: Project,
    editor: Editor,
    myFileContextInfo: FileContextInfo? = null,
): RequestContext {
    val triggerType = aTriggerType()
    val automatedTriggerType = if (triggerType == CodewhispererTriggerType.AutoTrigger) {
        listOf(
            CodeWhispererAutomatedTriggerType.IdleTime(),
            CodeWhispererAutomatedTriggerType.Enter(),
            CodeWhispererAutomatedTriggerType.SpecialChar('a'),
            CodeWhispererAutomatedTriggerType.IntelliSense()
        ).random()
    } else {
        CodeWhispererAutomatedTriggerType.Unknown()
    }

    return RequestContext(
        project,
        editor,
        TriggerTypeInfo(triggerType, automatedTriggerType),
        CaretPosition(Random.nextInt(), Random.nextInt()),
        fileContextInfo = myFileContextInfo ?: aFileContextInfo(),
        null,
        LatencyContext(
            Random.nextDouble(),
            Random.nextLong(),
            Random.nextLong(),
            aString()
        ),
        customizationArn = null,
        workspaceId = null,
        diagnostics = emptyList(),
    )
}

fun aProgrammingLanguage(): CodeWhispererProgrammingLanguage = listOf(
    CodeWhispererJava.INSTANCE,
    CodeWhispererPython.INSTANCE,
    CodeWhispererJavaScript.INSTANCE,
    CodeWhispererTypeScript.INSTANCE,
    CodeWhispererJsx.INSTANCE,
    CodeWhispererCsharp.INSTANCE,
    CodeWhispererKotlin.INSTANCE,
    CodeWhispererC.INSTANCE,
    CodeWhispererCpp.INSTANCE,
    CodeWhispererGo.INSTANCE,
    CodeWhispererPhp.INSTANCE,
    CodeWhispererRuby.INSTANCE,
    CodeWhispererScala.INSTANCE,
    CodeWhispererShell.INSTANCE,
    CodeWhispererSql.INSTANCE
).random()
