// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.json

import com.intellij.json.psi.JsonFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.runInEdtAndWait
import org.apache.commons.lang3.builder.ReflectionToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import software.aws.toolkits.jetbrains.services.cloudformation.CfnNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnParsedFile
import software.aws.toolkits.jetbrains.services.cloudformation.CfnProblem
import software.aws.toolkits.jetbrains.utils.rules.JavaCodeInsightTestFixtureRule
import kotlin.reflect.full.isSubclassOf
import kotlin.test.assertNotNull

@RunWith(Parameterized::class)
class JsonCfnParserTest(private val templateName: String) {
    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun data() = listOf(
            "completeExample.json",
            "conditions.json",
            "mappings.json",
            "mappingsMultipleValues.json",
            "metadata.json",
            "metadataWrongType.json",
            "outputs.json",
            "parameters.json",
            "resources.json",
            "transformString.json",
            "transformStringArray.json",
            "transformWrongType.json",
            "unknownSection.json",
            "unknownTopLevelResourceProperty.json",
            "unsupportedVersion.json",
            "version.json",
            "versionWrongType.json"
        )
    }

    @Rule
    @JvmField
    val projectRule = JavaCodeInsightTestFixtureRule()

    @Test
    fun runTest() {
        val jsonCfnService = JsonCfnService.getInstance(projectRule.project) ?: fail("Failed to get JSON service")

        val inputTemplate = loadFile(templateName) ?: fail("$templateName not found")
        val psiFile = projectRule.fixture.configureByText(templateName, inputTemplate)
        assertNotNull(psiFile)

        runInEdtAndWait {
            val parsedFile = jsonCfnService.parse(psiFile as JsonFile)
            assertNotNull(parsedFile)

            validateModel(parsedFile, templateName)
            validateProblems(psiFile, parsedFile, templateName)
        }
    }

    private fun loadFile(fileName: String) = JsonCfnParserTest::class.java.getResourceAsStream(fileName)?.bufferedReader()?.use {
        it.readText()
    }

    private fun validateModel(parsedFile: CfnParsedFile, templateName: String) {
        val expectedFile = templateName.replace(".json", ".model")
        val modelString = convertToString(parsedFile.root)
        val expectedModel = loadFile(expectedFile) ?: ""

        assertThat(modelString)
            .describedAs("File %s has unexpected content", expectedFile)
            .isEqualToNormalizingWhitespace(expectedModel)
    }

    private fun validateProblems(psiFile: PsiFile, parsedFile: CfnParsedFile, templateName: String) {
        val expectedFile = templateName.replace(".json", ".problems")
        // If no problem, output text should match input text
        val expectedProblems = loadFile(expectedFile) ?: loadFile(templateName) ?: fail("$expectedFile not found")

        assertThat(renderProblems(psiFile, parsedFile.formatProblems))
            .describedAs("File %s has unexpected content", expectedFile)
            .isEqualToNormalizingWhitespace(expectedProblems)
    }

    private fun convertToString(node: CfnNode): String = ShortRecursiveToString.toString(node)

    private fun renderProblems(psiFile: PsiFile, problems: List<CfnProblem>): String {
        val baseText = StringBuffer(psiFile.text)

        var offset = 0

        problems.forEach {
            val start = it.element.textOffset + offset

            val prefix = "<problem description=\"${it.description}\">"
            val suffix = "</problem>"

            baseText.insert(start, prefix)
            baseText.insert(start + prefix.length + it.element.textLength, suffix)

            // Increment the offset by how much text we inserted
            offset += prefix.length
            offset += suffix.length
        }

        return baseText.toString()
    }

    private class ShortRecursiveToString(private var offset: Int) : ToStringStyle() {
        companion object {
            fun toString(obj: Any?, offset: Int = 1): String =
                ReflectionToStringBuilder(obj, ShortRecursiveToString(offset)).toString()
        }

        private data class Entry(val key: Any?, val value: Any?)

        private val lineSep = '\n'
        private val spacer = "  "

        init {
            isUseShortClassName = true
            isUseIdentityHashCode = false

            updateIndents()
        }

        private fun updateIndents() {
            contentStart = "{" + lineSep + spacer.repeat(offset)
            contentEnd = lineSep + spacer.repeat(offset - 1) + "}"

            fieldSeparator = "," + lineSep + spacer.repeat(offset)

            arrayStart = "[" + lineSep + spacer.repeat(offset)
            arraySeparator = "," + lineSep + spacer.repeat(offset)
            arrayEnd = lineSep + spacer.repeat(offset - 1) + "]"
        }

        override fun appendDetail(buffer: StringBuffer, fieldName: String, map: MutableMap<*, *>) {
            appendDetail(buffer, fieldName, "<TRUNCATED MAP>")
        }

        override fun appendDetail(buffer: StringBuffer, fieldName: String?, col: Collection<*>) {
            this.appendDetail(buffer, fieldName, col.toTypedArray())
        }

        override fun appendDetail(buffer: StringBuffer, fieldName: String?, array: Array<Any?>) {
            offset += 1
            updateIndents()

            super.appendDetail(buffer, fieldName, array)

            offset -= 1
            updateIndents()
        }

        override fun appendDetail(buffer: StringBuffer, fieldName: String?, value: Any) {
            if (value is Entry || value.javaClass.kotlin.isSubclassOf(CfnNode::class)) {
                buffer.append(toString(value, offset + 1))
            } else {
                super.appendDetail(buffer, fieldName, value)
            }
        }
    }
}
