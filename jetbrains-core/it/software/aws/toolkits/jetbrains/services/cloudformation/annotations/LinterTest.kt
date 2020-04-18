// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.annotations

import com.intellij.lang.annotation.HighlightSeverity
import org.junit.Assert
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat
import org.junit.BeforeClass
import java.io.File

class LinterTest {

    companion object {
        var errors: List<ErrorAnnotation> = mutableListOf()

        @BeforeClass
        @JvmStatic
        fun setup() {
            val cloudFormationLinter = Linter()
            val templatePath = File(LinterTest::class.java.getResource("/linterInput.json").path).toString()
            val initialAnnotationResults = InitialAnnotationResults(templatePath)
            errors = cloudFormationLinter.execute(initialAnnotationResults)
        }
    }

    @Test
    fun testNumberOfErrors() {
        assertThat(errors.size).isEqualTo(1)
    }

    @Test
    fun testMessage() {
        val e = errors[0]
        assertThat(e.message).isEqualTo("Invalid or unsupported Type undefined for resource S3Bucket in us-east-1")
    }

    @Test
    fun testRuleId() {
        val e = errors[0]
        assertThat(e.linterRule!!.id).isEqualTo("E3001")
    }

    @Test
    fun testSeverity() {
        val e = errors[0]
        Assert.assertEquals(HighlightSeverity.ERROR, e.severity)
    }
}
