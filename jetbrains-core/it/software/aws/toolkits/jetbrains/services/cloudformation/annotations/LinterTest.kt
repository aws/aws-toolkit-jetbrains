// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.annotations

import com.intellij.json.JsonLanguage
import com.intellij.json.psi.JsonFile
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.runInEdtAndGet
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import software.aws.toolkits.jetbrains.utils.rules.JavaCodeInsightTestFixtureRule

class LinterTest {

    @Rule
    @JvmField
    val projectRule = JavaCodeInsightTestFixtureRule()

    @Test
    fun getsRightNumberOfErrors() {
        val initialAnnotationResults = InitialAnnotationResults(jsonFile())
        val cloudFormationLints = Linter.execute(initialAnnotationResults)

        assertThat(cloudFormationLints.size).isEqualTo(1)
    }

    private fun jsonFile(): JsonFile = runInEdtAndGet {
        PsiFileFactory.getInstance(projectRule.project).createFileFromText(
            JsonLanguage.INSTANCE, """
{
  "Resources" : {
    "S3Bucket" : {
      "Type" : "undefined"
    }
  }
}
        """.trimIndent()
        ) as JsonFile
    }
}
