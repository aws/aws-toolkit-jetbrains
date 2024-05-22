// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils

import com.intellij.json.JsonLanguage
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.verify
import software.aws.toolkits.core.utils.convertMarkdownToHTML
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanIssue
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.SuggestedFix

class TextUtilsTest {
    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Test
    fun textGetsFormatted() {
        @Language("JSON")
        val actual =
            """
            {
              "hello":
                      "world"}
            """.trimIndent()

        @Language("JSON")
        val expected =
            """
            {
              "hello": "world"
            }
            """.trimIndent()

        lateinit var formatted: String
        runInEdtAndWait {
            formatted = formatText(projectRule.project, JsonLanguage.INSTANCE, actual)
        }
        assertThat(formatted).isEqualTo(expected)
    }

    @Test
    fun canConvertToTitleHumanReadable() {
        assertThat("CREATE_COMPLETE".toHumanReadable()).isEqualTo("Create Complete")
        assertThat("UPDATE_IN_PROGRESS".toHumanReadable()).isEqualTo("Update In Progress")
    }

    @Test
    fun canConvertMarkdownToHTML() {
        @Language("md")
        val input = """
            # heading 1
            ## heading 2
            
           ```js 
           console.log("hello world");
           ```
        """.trimIndent()

        @Language("html")
        val expected = """
            <h1>heading 1</h1>
            <h2>heading 2</h2>
            <div class="code-block"><pre><code class="language-js">console.log(&quot;hello world&quot;);
            </code></pre></div>
            
        """.trimIndent()

        val actual = convertMarkdownToHTML(input)
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun canRenderDiffsWithCustomRenderer() {
        @Language("md")
        val input = """
           ```diff
             line 1
           - line 2
           + line 3
             line 4
           ```
        """.trimIndent()

        @Language("html")
        val expected = """
            <div class="code-block"><div><pre>  line 1</pre></div><div class="deletion"><pre>- line 2</pre></div><div class="addition"><pre>+ line 3</pre></div><div><pre>  line 4</pre></div><div><pre></pre></div></div>
            
        """.trimIndent()

        val actual = convertMarkdownToHTML(input)
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun extractChangesWithSingleLineDeletion() {
        val suggestedFixes = listOf(SuggestedFix(description = "MockDescription", code = "-oldLine\n+newLine"))
        val issue = mock(CodeWhispererCodeScanIssue::class.java)
        `when`(issue.suggestedFixes).thenReturn(suggestedFixes)

        val (linesToDelete, linesToInsert) = extractChanges(issue)

        assertThat(linesToDelete).isEqualTo(1)
        assertThat(linesToInsert).isEqualTo(listOf("newLine"))
    }

    @Test
    fun extractChangesWithMultipleLineDeletionAndInsertion() {
        val suggestedFixes = listOf(SuggestedFix(description = "MockDescription", code = "-oldLine1\n-oldLine2\n+newLine1\n+newLine2"))
        val issue = mock(CodeWhispererCodeScanIssue::class.java)
        `when`(issue.suggestedFixes).thenReturn(suggestedFixes)

        val (linesToDelete, linesToInsert) = extractChanges(issue)

        assertThat(linesToDelete).isEqualTo(2)
        assertThat(linesToInsert).isEqualTo(listOf("newLine1", "newLine2"))
    }

    @Test
    fun extractChangesWithEmptySuggestedFixes() {
        val emptyStringList: List<String> = emptyList()
        val issue = mock(CodeWhispererCodeScanIssue::class.java)
        `when`(issue.suggestedFixes).thenReturn(emptyList())

        val (linesToDelete, linesToInsert) = extractChanges(issue)

        assertThat(linesToDelete).isEqualTo(0)
        assertThat(linesToInsert).isEqualTo(emptyStringList)
    }

    @Test
    fun updateEditorDocumentWithSingleLineDeletionAndInsertion() {
        val document = mock(Document::class.java)
        val project = mock(Project::class.java)
        val issue = mock(CodeWhispererCodeScanIssue::class.java)
        val suggestedFixes = listOf(SuggestedFix(description = "MockDescription", code = "-oldLine\n+newLine"))
        `when`(issue.startLine).thenReturn(1)
        `when`(issue.suggestedFixes).thenReturn(suggestedFixes)
        `when`(document.getLineStartOffset(0)).thenReturn(0)
        `when`(document.getLineEndOffset(0)).thenReturn(10)

        val psiDocumentManager = mock(PsiDocumentManager::class.java)
        doNothing().`when`(psiDocumentManager).commitDocument(document)
        Mockito.`when`(PsiDocumentManager.getInstance(project)).thenReturn(psiDocumentManager)

        runInEdtAndWait {
            updateEditorDocument(document, issue, project)
        }

        verify(document).replaceString(0, 10, "newLine")
    }

    @Test
    fun updateEditorDocumentWithMultipleLineDeletionAndInsertion() {
        val document = mock(Document::class.java)
        val project = mock(Project::class.java)
        val issue = mock(CodeWhispererCodeScanIssue::class.java)
        val suggestedFixes = listOf(SuggestedFix(description = "MockDescription", code = "-oldLine1\n-oldLine2\n+newLine1\n+newLine2"))
        `when`(issue.startLine).thenReturn(1)
        `when`(issue.suggestedFixes).thenReturn(suggestedFixes)
        `when`(document.getLineStartOffset(0)).thenReturn(20)
        `when`(document.getLineEndOffset(1)).thenReturn(40)

        val psiDocumentManager = mock(PsiDocumentManager::class.java)
        doNothing().`when`(psiDocumentManager).commitDocument(document)
        Mockito.`when`(PsiDocumentManager.getInstance(project)).thenReturn(psiDocumentManager)

        runInEdtAndWait {
            updateEditorDocument(document, issue, project)
        }

        verify(document).replaceString(20, 40, "newLine1\nnewLine2")
    }
}
