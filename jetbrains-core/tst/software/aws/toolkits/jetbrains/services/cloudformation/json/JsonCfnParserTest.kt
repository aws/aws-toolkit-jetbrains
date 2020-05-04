// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.json

import com.intellij.json.psi.JsonFile
import com.intellij.testFramework.runInEdtAndGet
import org.apache.commons.lang3.builder.MultilineRecursiveToStringStyle
import org.apache.commons.lang3.builder.ReflectionToStringBuilder
import org.assertj.core.api.Assertions.fail
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.services.cloudformation.CfnNode
import software.aws.toolkits.jetbrains.utils.rules.JavaCodeInsightTestFixtureRule
import kotlin.reflect.full.isSubclassOf

class JsonCfnParserTest {
    @Rule
    @JvmField
    val projectRule = JavaCodeInsightTestFixtureRule()

    @Test
    fun parameters() = runTest("parameters.json")

    @Test
    fun metadata() = runTest("metadata.json")

    @Test
    fun transformString() = runTest("transformString.json")

    private fun runTest(templateName: String) {
        val jsonCfnService = JsonCfnService.getInstance(projectRule.project) ?: fail("Failed to get JSON service")

        JsonCfnParserTest::class.java.getResourceAsStream(templateName)?.use {
            val psiFile = projectRule.fixture.configureByText(templateName, it.bufferedReader().readText())

            val parsedFile = runInEdtAndGet {
                jsonCfnService.parse(psiFile as JsonFile)
            }

            println(convertToString(parsedFile?.root))
        } ?: fail("$templateName not found")
    }

    private fun convertToString(node: CfnNode?): String = ReflectionToStringBuilder.reflectionToString(node, ShortRecursiveToString())

    inner class ShortRecursiveToString : MultilineRecursiveToStringStyle() {
        init {
            isUseShortClassName = true
            isUseIdentityHashCode = false
        }

        override fun appendDetail(buffer: StringBuffer?, fieldName: String?, coll: Collection<*>) {
            appendDetail(buffer, fieldName, coll.toTypedArray())
        }

        override fun accept(clazz: Class<*>): Boolean = clazz.kotlin.isSubclassOf(CfnNode::class)
    }
}
