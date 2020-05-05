// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.json

import com.intellij.json.psi.JsonFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.runInEdtAndWait
import org.apache.commons.lang3.builder.MultilineRecursiveToStringStyle
import org.apache.commons.lang3.builder.ReflectionToStringBuilder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.services.cloudformation.CfnNode
import software.aws.toolkits.jetbrains.services.cloudformation.CfnParsedFile
import software.aws.toolkits.jetbrains.services.cloudformation.CfnProblem
import software.aws.toolkits.jetbrains.utils.rules.JavaCodeInsightTestFixtureRule
import kotlin.reflect.full.isSubclassOf
import kotlin.test.assertNotNull

class JsonCfnParserTest {
    @Rule
    @JvmField
    val projectRule = JavaCodeInsightTestFixtureRule()

    @Test
    fun parameters() = runTest("parameters.json")

    @Test
    fun metadata() = runTest("metadata.json")

    @Test
    fun metadataWrongType() = runTest("metadataWrongType.json")

    @Test
    fun transformString() = runTest("transformString.json")

    @Test
    fun transformStringArray() = runTest("transformStringArray.json")

    @Test
    fun transformWrongType() = runTest("transformWrongType.json")

    @Test
    fun mappings() = runTest("mappings.json")

    @Test
    fun mappingsMultipleValues() = runTest("mappingsMultipleValues.json")

    @Test
    fun version() = runTest("version.json")

    @Test
    fun unsupportedVersion() = runTest("unsupportedVersion.json")

    @Test
    fun versionWrongType() = runTest("versionWrongType.json")

    @Test
    fun outputs() = runTest("outputs.json")

    @Test
    fun conditions() = runTest("conditions.json")

    @Test
    fun resources() = runTest("resources.json")

    @Test
    fun unknownTopLevelResourceProperty() = runTest("unknownTopLevelResourceProperty.json")

    @Test
    fun unknownSection() = runTest("unknownSection.json")

    @Test
    fun completeExample() = runTest("completeExample.json")

    private fun runTest(templateName: String) {
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

    private fun convertToString(node: CfnNode): String = ReflectionToStringBuilder.reflectionToString(node, ShortRecursiveToString())

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

    inner class ShortRecursiveToString : MultilineRecursiveToStringStyle() {
        init {
            isUseShortClassName = true
            isUseIdentityHashCode = false
        }

        override fun appendDetail(buffer: StringBuffer?, fieldName: String?, coll: Collection<*>) {
            appendDetail(buffer, fieldName, coll.toTypedArray())
        }

        override fun appendDetail(buffer: StringBuffer, fieldName: String, map: MutableMap<*, *>) {
            // This library is kinda buggy (Recursive toString doesnt copy over settings into child toString)
            // and so maps dont work. We dont really care about the Map field (allTopLevelProperties) anyway
            // TODO: Is there a better lib?
            appendDetail(buffer, fieldName, "<TRUNCATED MAP>")
        }

        override fun accept(clazz: Class<*>): Boolean = clazz.kotlin.isSubclassOf(CfnNode::class)
    }
}
