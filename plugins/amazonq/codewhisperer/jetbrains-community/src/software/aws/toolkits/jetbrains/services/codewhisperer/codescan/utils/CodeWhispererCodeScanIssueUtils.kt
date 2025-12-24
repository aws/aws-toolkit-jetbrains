// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.JBColor
import icons.AwsIcons
import software.amazon.awssdk.services.codewhispererruntime.model.CodeWhispererRuntimeException
import software.amazon.q.core.utils.convertMarkdownToHTML
import software.amazon.q.core.utils.debug
import software.amazon.q.core.utils.getLogger
import software.amazon.q.jetbrains.ToolkitPlaces
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionReference
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionReferencePosition
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanHighlightingFilesPanel
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanIssue
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanManager
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.SuggestedFix
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.context.CodeScanIssueDetailsDisplayType
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererClientAdaptor
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExplorerActionManager
import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererProgrammingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.language.programmingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.CodeWhispererTelemetryService
import software.aws.toolkits.jetbrains.services.codewhisperer.toolwindow.CodeWhispererCodeReferenceManager
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.CODE_SCAN_ISSUE_TITLE_MAX_LENGTH
import software.aws.toolkits.jetbrains.services.codewhisperer.util.runIfIdcConnectionOrTelemetryEnabled
import software.amazon.q.jetbrains.utils.applyPatch
import software.amazon.q.jetbrains.utils.notifyError
import software.amazon.q.jetbrains.utils.pluginAwareExecuteOnPooledThread
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.CodeFixAction
import software.aws.toolkits.telemetry.Result
import javax.swing.Icon

val codeBlockBackgroundColor = JBColor.namedColor("Editor.background", JBColor(0xf7f8fa, 0x2b2d30))
val codeBlockForegroundColor = JBColor.namedColor("Editor.foreground", JBColor(0x808080, 0xdfe1e5))
val codeBlockBorderColor = JBColor.namedColor("borderColor", JBColor(0xebecf0, 0x1e1f22))
val deletionBackgroundColor = JBColor.namedColor("FileColor.Rose", JBColor(0xf5c2c2, 0x511e1e))
val deletionForegroundColor = JBColor.namedColor("Label.errorForeground", JBColor(0xb63e3e, 0xfc6479))
val additionBackgroundColor = JBColor.namedColor("FileColor.Green", JBColor(0xdde9c1, 0x394323))
val additionForegroundColor = JBColor.namedColor("Label.successForeground", JBColor(0x42a174, 0xacc49e))
val metaBackgroundColor = JBColor.namedColor("FileColor.Blue", JBColor(0xeaf6ff, 0x4f556b))
val metaForegroundColor = JBColor.namedColor("Label.infoForeground", JBColor(0x808080, 0x8C8C8C))

private val LOG = getLogger<CodeWhispererCodeScanHighlightingFilesPanel>()
private val hanldeIssueCommandContextDataKey = DataKey.create<MutableMap<String, String>>("amazonq.codescan.handleIssueCommandContext")
private val hanldeIssueCommandActionDataKey = DataKey.create<String>("amazonq.codescan.handleIssueCommandAction")

enum class IssueSeverity(val displayName: String) {
    CRITICAL("Critical"),
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low"),
    INFO("Info"),
}

enum class IssueGroupingStrategy(val displayName: String) {
    SEVERITY("Severity"),
    FILE_LOCATION("File Location"),
}

private enum class IssueCommandAction(val displayName: String) {
    EXPLAIN_ISSUE("explainIssue"),
    APPLY_FIX("applyFix"),
}

fun getCodeScanIssueDetailsHtml(
    issue: CodeWhispererCodeScanIssue,
    display: CodeScanIssueDetailsDisplayType,
    fixGenerationState: CodeWhispererConstants.FixGenerationState = CodeWhispererConstants.FixGenerationState.COMPLETED,
    isCopied: Boolean = false,
    project: Project,
    showReferenceWarning: Boolean? = false,
): String {
    val suggestedFix = issue.suggestedFixes.firstOrNull()

    val cweLinks = if (issue.relatedVulnerabilities.isNotEmpty()) {
        issue.relatedVulnerabilities.joinToString(", ") { cwe ->
            "<a href=\"https://cwe.mitre.org/data/definitions/${cwe.split("-").last()}.html\">$cwe</a>"
        }
    } else {
        "-"
    }

    val projectRoot = project.basePath?.let { VirtualFileManager.getInstance().findFileByUrl(VfsUtilCore.pathToUrl(it)) } ?: project.guessProjectDir()
    val filePathString = projectRoot?.let { VfsUtil.getRelativePath(issue.file, it) } ?: issue.file.path

    val fileLink = "<a href=amazonq://issue/openFile-${issue.findingId}>${ filePathString } [Ln ${issue.startLine}]</a>"

    val detectorLibraryLink = issue.recommendation.url?.let { "<a href=\"${issue.recommendation.url}\">${issue.detectorName}</a>" } ?: "-"
    val detectorSection = """
            <br />
            <hr />
            <table>
                <thead>
                    <tr>
                        <th>${message("codewhisperer.codescan.cwe_label")}</th>
                        <th>${message("codewhisperer.codescan.detector_library_label")}</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td>$cweLinks</td>
                        <td>$detectorLibraryLink</td>
                    </tr>
                </tbody>
            </table>
            <table>
                <thead>
                    <tr>      
                        <th>${message("codewhisperer.codescan.file_path_label")}</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td>$fileLink</td>
                    </tr>
                </tbody>
            </table>
    """.trimIndent()

    val suggestedFixSection = if (showReferenceWarning == false) {
        createSuggestedFixSection(issue, suggestedFix, isCopied)
    } else {
        """
         <div align="center" bgcolor="#2b2b2b" style="margin: 20px;">
             Your settings do not allow code generation with references.
        </div>
        """.trimIndent()
    }

    val fixLoadingSection = """
        <a name="fixLoadingSection"></a>
        <div align="center" bgcolor="#2b2b2b" style="margin: 20px;">
            <font size="7" color="#ffffff" face="Arial">
                &nbsp;&nbsp;&nbsp;&nbsp;...&nbsp;&nbsp;&nbsp;&nbsp;
            </font>
        </div>
    """.trimIndent()

    val fixFailureSection = """
        <a name="fixFailureSection"></a>
        <div align="center" bgcolor="#2b2b2b" style="margin: 20px;">
            <font size="4" color="#e6e6e6" face="Arial">
                <br>Amazon Q failed to generate fix. Please try again<br>
            </font>
        </div>
    """.trimIndent()

    val commonContent = """
        |${issue.recommendation.text}
        |
        |$detectorSection
        |
        |${when (fixGenerationState) {
        CodeWhispererConstants.FixGenerationState.COMPLETED -> suggestedFixSection.orEmpty()
        CodeWhispererConstants.FixGenerationState.GENERATING -> fixLoadingSection
        CodeWhispererConstants.FixGenerationState.FAILED -> fixFailureSection
    }}
    """.trimMargin()

    if (display == CodeScanIssueDetailsDisplayType.EditorPopup) {
        return convertMarkdownToHTML(
            """
            |$commonContent
            """.trimMargin()
        )
    }

    return convertMarkdownToHTML(commonContent)
}

private fun createSuggestedFixSection(issue: CodeWhispererCodeScanIssue, suggestedFix: SuggestedFix?, isCopied: Boolean = false): String? = suggestedFix?.let {
    val isFixDescriptionAvailable = it.description.isNotBlank() &&
        it.description.trim() != "Suggested remediation:"
    """
            |<hr />
            |
            |### ${message("codewhisperer.codescan.suggested_fix_label")}
            |
            |<br />
            |
            |<div class="code-block">
            |<div class="code-content">
            |
            |```diff
            |${it.code.trim()}
            |``` 
            |</div>
            |<a name="codeFixActions"></a>
            |<div>
            |    <a href="amazonq://issue/openDiff-${issue.findingId}">
            |        <font size="+1"><i>&#x2194;</i></font> <b>Open Diff</b>
            |    </a>
            |    &nbsp;&nbsp;&nbsp;&nbsp;
            |    <a href="amazonq://issue/copyDiff-${issue.findingId}">
            |        <font size="+1"><i>&#x1F4CB;</i></font> <b>${if (isCopied) "Copied!" else "Copy"}</b>
            |    </a>
            |</div>
            |</div>
            |
            |${
        if (isFixDescriptionAvailable) {
            "|### ${
                message(
                    "codewhisperer.codescan.suggested_fix_description"
                )
            }\n${it.description}"
        } else {
            ""
        }
    }
    """.trimMargin()
}

fun explainIssue(issue: CodeWhispererCodeScanIssue) {
    handleIssueCommand(issue, IssueCommandAction.EXPLAIN_ISSUE)
}

fun applyFix(issue: CodeWhispererCodeScanIssue) {
    handleIssueCommand(issue, IssueCommandAction.APPLY_FIX)
}

private fun handleIssueCommand(issue: CodeWhispererCodeScanIssue, action: IssueCommandAction) {
    val handleIssueCommandContext = mutableMapOf(
        "title" to issue.title,
        "description" to issue.description.markdown,
        "code" to issue.codeText,
        "fileName" to issue.file.name,
        "startLine" to issue.startLine.toString(),
        "endLine" to issue.endLine.toString(),
        "recommendation" to jacksonObjectMapper().writeValueAsString(issue.recommendation),
        "suggestedFixes" to jacksonObjectMapper().writeValueAsString(issue.suggestedFixes),
        "codeSnippet" to jacksonObjectMapper().writeValueAsString(issue.codeSnippet),
        "findingId" to issue.findingId,
        "ruleId" to issue.ruleId.orEmpty(),
        "detectorId" to issue.detectorId,
        "autoDetected" to issue.autoDetected.toString(),
    )
    val actionEvent = AnActionEvent.createFromInputEvent(
        null,
        ToolkitPlaces.EDITOR_PSI_REFERENCE,
        null,
        SimpleDataContext.builder()
            .add(hanldeIssueCommandContextDataKey, handleIssueCommandContext)
            .add(CommonDataKeys.PROJECT, issue.project)
            .add(hanldeIssueCommandActionDataKey, action.displayName)
            .build()
    )
    ActionManager.getInstance().getAction("aws.amazonq.handleCodeScanIssueCommand").actionPerformed(actionEvent)
}

fun openDiff(issue: CodeWhispererCodeScanIssue) {
    val diffContentFactory = DiffContentFactory.getInstance()
    val document = FileDocumentManager.getInstance().getDocument(issue.file)
    document?.text?.let { documentContent ->
        val updatedContent = applyPatch(issue.suggestedFixes[0].code, documentContent, issue.file.name)
        val (originalContent, suggestedContent) = try {
            diffContentFactory.create(documentContent) to
                diffContentFactory.create(updatedContent)
        } catch (e: Exception) {
            ApplicationManager.getApplication().executeOnPooledThread {
                CodeWhispererTelemetryService.getInstance().sendCodeScanIssueApplyFixEvent(
                    issue,
                    Result.Failed,
                    e.message,
                    codeFixAction = CodeFixAction.OpenDiff
                )
            }
            return@let null
        }

        val request = SimpleDiffRequest(
            "Amazon Q Code Suggestion Diff",
            suggestedContent,
            originalContent,
            "Suggested fix",
            "Original code"
        ).apply {
            putUserData(DiffUserDataKeys.MERGE_EDITOR_FLAG, true)

            putUserData(DiffUserDataKeys.DO_NOT_IGNORE_WHITESPACES, true)

            putUserData(DiffUserDataKeys.ENABLE_SEARCH_IN_CHANGES, true)
            putUserData(DiffUserDataKeys.GO_TO_SOURCE_DISABLE, false)

            putUserData(DiffUserDataKeys.ALIGNED_TWO_SIDED_DIFF, true)
            putUserData(DiffUserDataKeys.FORCE_READ_ONLY_CONTENTS, booleanArrayOf(true, false))
            putUserData(DiffUserDataKeys.FORCE_READ_ONLY, false)
        }
        ApplicationManager.getApplication().invokeLater {
            DiffManager.getInstance().showDiff(
                issue.project,
                request
            )
        }
    }
    ApplicationManager.getApplication().executeOnPooledThread {
        CodeWhispererTelemetryService.getInstance().sendCodeScanIssueApplyFixEvent(issue, Result.Succeeded, codeFixAction = CodeFixAction.OpenDiff)
    }
}

fun truncateIssueTitle(title: String): String = title.takeUnless { it.length <= CODE_SCAN_ISSUE_TITLE_MAX_LENGTH }?.let {
    it.substring(0, CODE_SCAN_ISSUE_TITLE_MAX_LENGTH - 3) + "..."
} ?: title

fun sendCodeRemediationTelemetryToServiceApi(
    project: Project,
    language: CodeWhispererProgrammingLanguage?,
    codeScanRemediationEventType: String?,
    detectorId: String?,
    findingId: String?,
    ruleId: String?,
    component: String?,
    reason: String?,
    result: String?,
    includesFix: Boolean?,
) {
    runIfIdcConnectionOrTelemetryEnabled(project) {
        pluginAwareExecuteOnPooledThread {
            try {
                val response = CodeWhispererClientAdaptor.getInstance(project)
                    .sendCodeScanRemediationTelemetry(
                        language,
                        codeScanRemediationEventType,
                        detectorId,
                        findingId,
                        ruleId,
                        component,
                        reason,
                        result,
                        includesFix
                    )
                LOG.debug { "Successfully sent code scan remediation telemetry. RequestId: ${response.responseMetadata().requestId()}" }
            } catch (e: Exception) {
                val requestId = if (e is CodeWhispererRuntimeException) e.requestId() else null
                LOG.debug(e) {
                    "Failed to send code scan remediation telemetry. RequestId: $requestId"
                }
            }
        }
    }
}

fun applySuggestedFix(project: Project, issue: CodeWhispererCodeScanIssue) {
    try {
        val manager = CodeWhispererCodeReferenceManager.getInstance(issue.project)
        WriteCommandAction.runWriteCommandAction(issue.project) {
            val document = FileDocumentManager.getInstance().getDocument(issue.file) ?: return@runWriteCommandAction

            val documentContent = document.text
            val updatedContent = applyPatch(issue.suggestedFixes[0].code, documentContent, issue.file.name)
            document.replaceString(document.getLineStartOffset(0), document.getLineEndOffset(document.lineCount - 1), updatedContent)
            PsiDocumentManager.getInstance(issue.project).commitDocument(document)
            issue.suggestedFixes[0].references.forEach { reference ->
                LOG.debug { "Applied fix with reference: $reference" }
                val originalContent = updatedContent.substring(reference.recommendationContentSpan().start(), reference.recommendationContentSpan().end())
                LOG.debug { "Original content from reference span: $originalContent" }
                // TODO flare: hook codescan references with flare correctly, this is only a compile error fix which is not tested
                manager.addReferenceLogPanelEntry(
                    reference = InlineCompletionReference(
                        referenceName = reference.repository(),
                        referenceUrl = reference.url(),
                        licenseName = reference.licenseName(),
                        position = InlineCompletionReferencePosition(
                            startCharacter = reference.recommendationContentSpan().start(),
                            endCharacter = reference.recommendationContentSpan().end(),
                        ),
                    ),
                    null,
                    null,
                    originalContent.split("\n")
                )
            }
        }
        if (issue.suggestedFixes[0].references.isNotEmpty()) {
            manager.toolWindow?.show()
        }
        if (CodeWhispererExplorerActionManager.getInstance().isAutoEnabledForCodeScan()) {
            CodeWhispererCodeScanManager.getInstance(issue.project).removeIssueByFindingId(issue, issue.findingId)
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            CodeWhispererTelemetryService.getInstance().sendCodeScanIssueApplyFixEvent(issue, Result.Succeeded, codeFixAction = CodeFixAction.ApplyFix)
        }
        sendCodeRemediationTelemetryToServiceApi(
            project,
            issue.file.programmingLanguage(),
            "CODESCAN_ISSUE_APPLY_FIX",
            issue.detectorId,
            issue.findingId,
            issue.ruleId,
            null,
            null,
            Result.Succeeded.toString(),
            issue.suggestedFixes.isNotEmpty()
        )
        sendCodeFixGeneratedTelemetryToServiceAPI(issue, true)
    } catch (e: Throwable) {
        notifyError(message("codewhisperer.codescan.fix_applied_fail", e))
        LOG.debug(e) { "Apply fix command failed." }
        ApplicationManager.getApplication().executeOnPooledThread {
            CodeWhispererTelemetryService.getInstance().sendCodeScanIssueApplyFixEvent(issue, Result.Failed, e.message, codeFixAction = CodeFixAction.ApplyFix)
            sendCodeRemediationTelemetryToServiceApi(
                project,
                issue.file.programmingLanguage(),
                "CODESCAN_ISSUE_APPLY_FIX",
                issue.detectorId,
                issue.findingId,
                issue.ruleId,
                null,
                e.message,
                Result.Failed.toString(),
                issue.suggestedFixes.isNotEmpty()
            )
        }
    }
}

fun getSeverityIcon(issue: CodeWhispererCodeScanIssue): Icon? = when (issue.severity) {
    "Info" -> AwsIcons.Resources.CodeWhisperer.SEVERITY_INFO
    "Low" -> AwsIcons.Resources.CodeWhisperer.SEVERITY_LOW
    "Medium" -> AwsIcons.Resources.CodeWhisperer.SEVERITY_MEDIUM
    "High" -> AwsIcons.Resources.CodeWhisperer.SEVERITY_HIGH
    "Critical" -> AwsIcons.Resources.CodeWhisperer.SEVERITY_CRITICAL
    else -> null
}

fun sendCodeFixGeneratedTelemetryToServiceAPI(
    issue: CodeWhispererCodeScanIssue,
    acceptFix: Boolean,
) {
    runIfIdcConnectionOrTelemetryEnabled(issue.project) {
        pluginAwareExecuteOnPooledThread {
            try {
                val client = CodeWhispererClientAdaptor.getInstance(issue.project)
                if (acceptFix) {
                    val acceptFixResponse = client.sendCodeFixAcceptanceTelemetry(
                        issue.file.programmingLanguage(),
                        issue.suggestedFixes.first().codeFixJobId,
                        issue.ruleId,
                        issue.detectorId,
                        issue.findingId,
                        issue.suggestedFixes.first().code.split("\n").size - 1,
                        issue.suggestedFixes.first().code.length
                    )
                    LOG.debug {
                        "Successfully sent code fix acceptance telemetry. RequestId: ${
                            acceptFixResponse.responseMetadata().requestId()
                        }"
                    }
                } else {
                    val generateFixResponse = client.sendCodeFixGenerationTelemetry(
                        issue.file.programmingLanguage(),
                        issue.suggestedFixes.first().codeFixJobId,
                        issue.ruleId,
                        issue.detectorId,
                        issue.findingId,
                        issue.suggestedFixes.first().code.split("\n").size - 1,
                        issue.suggestedFixes.first().code.length
                    )
                    LOG.debug {
                        "Successfully sent code fix generated telemetry. RequestId: ${
                            generateFixResponse.responseMetadata().requestId()
                        }"
                    }
                }
            } catch (e: Exception) {
                val requestId = if (e is CodeWhispererRuntimeException) e.requestId() else null
                LOG.debug { "Failed to send code fix telemetry. RequestId: $requestId, ErrorMessage: ${e.message}" }
            }
        }
    }
}
