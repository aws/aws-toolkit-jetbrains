// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.annotations

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.xml.XMLLanguage
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.runInEdtAndGet
import com.nhaarman.mockitokotlin2.mock
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.psi.YAMLFile
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.utils.rules.JavaCodeInsightTestFixtureRule

class CloudFormationLintAnnotatorTest {

    @Rule
    @JvmField
    val projectRule = JavaCodeInsightTestFixtureRule()

    val cloudFormationLintAnnotator = CloudFormationLintAnnotator()

    val annotationHolder = mock<AnnotationHolder>()

    @Test
    fun canAnnotate() {
        val psiFile = runInEdtAndGet {
            PsiFileFactory.getInstance(projectRule.project).createFileFromText(
                YAMLLanguage.INSTANCE, """
Resources:
  s3Bucket:
    Type: undef
        """.trimIndent()
            ) as YAMLFile
        }

        val initialAnnotationResults = cloudFormationLintAnnotator.collectInformation(psiFile)
        assertThat(initialAnnotationResults).isNotNull
        val errors = cloudFormationLintAnnotator.doAnnotate(initialAnnotationResults)
        assertThat(errors).isNotNull
        assertThat(errors.size).isEqualTo(1)
        runInEdtAndGet {
            cloudFormationLintAnnotator.apply(psiFile, errors, annotationHolder)
        }
    }

    @Test
    fun doesNotAnnotate() {
        val psiFile = runInEdtAndGet {
            PsiFileFactory.getInstance(projectRule.project).createFileFromText(
                XMLLanguage.INSTANCE, """
<demo>test</demo>
        """.trimIndent()
            ) as XmlFile
        }
        val initialAnnotationResults = cloudFormationLintAnnotator.collectInformation(psiFile)
        assertThat(initialAnnotationResults).isNotNull
        val errors = cloudFormationLintAnnotator.doAnnotate(initialAnnotationResults)
        assertThat(errors).isNotNull
        assertThat(errors.size).isEqualTo(0)
    }
}
