// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils

import com.intellij.json.JsonLanguage
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.core.utils.convertMarkdownToHTML

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
}
