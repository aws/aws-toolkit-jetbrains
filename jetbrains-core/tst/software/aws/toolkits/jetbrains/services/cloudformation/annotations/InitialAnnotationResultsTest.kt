// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.annotations

import com.intellij.json.JsonLanguage
import com.intellij.json.psi.JsonFile
import com.intellij.lang.xml.XMLLanguage
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.runInEdtAndGet
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.psi.YAMLFile
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.utils.rules.JavaCodeInsightTestFixtureRule
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InitialAnnotationResultsTest {

    @Rule
    @JvmField
    val projectRule = JavaCodeInsightTestFixtureRule()

    @Test
    fun isCloudFormationTemplate_Json_ResourcesAndType() {
        val psiFile = runInEdtAndGet {
            PsiFileFactory.getInstance(projectRule.project).createFileFromText(
                JsonLanguage.INSTANCE, """
{
    "Resources" : {
         "S3Bucket" : {
                "Type" : "AWS::S3::Bucket"
        }
    }
}
        """.trimIndent()
            ) as JsonFile
        }

        val initialAnnotationResults = InitialAnnotationResults(psiFile)

        assertTrue(initialAnnotationResults.isCloudFormationTemplate)
    }

    @Test
    fun isNotCloudFormationTemplate_Json_ResourcesButNoType() {
        val psiFile = runInEdtAndGet {
            PsiFileFactory.getInstance(projectRule.project).createFileFromText(
                JsonLanguage.INSTANCE, """
{
    "Resources" : {
         "S3Bucket" : {}
    }
}
        """.trimIndent()
            ) as JsonFile
        }

        val initialAnnotationResults = InitialAnnotationResults(psiFile)

        assertFalse(initialAnnotationResults.isCloudFormationTemplate)
    }

    @Test
    fun isCloudFormationTemplate_Json_HasVersionKey() {
        val psiFile = runInEdtAndGet {
            PsiFileFactory.getInstance(projectRule.project).createFileFromText(
                JsonLanguage.INSTANCE, """
{
  "AWSTemplateFormatVersion" : "2010-09-09"
}
        """.trimIndent()
            ) as JsonFile
        }

        val initialAnnotationResults = InitialAnnotationResults(psiFile)

        assertTrue(initialAnnotationResults.isCloudFormationTemplate)
    }

    @Test
    fun isNotCloudFormationTemplate_Json() {
        val psiFile = runInEdtAndGet {
            PsiFileFactory.getInstance(projectRule.project).createFileFromText(
                JsonLanguage.INSTANCE, """
{
  "unrecognized" : "2010-09-09"
}
        """.trimIndent()
            ) as JsonFile
        }

        val initialAnnotationResults = InitialAnnotationResults(psiFile)

        assertFalse(initialAnnotationResults.isCloudFormationTemplate)
    }

    @Test
    fun isNotCloudFormationTemplate_Yaml() {
        val psiFile = runInEdtAndGet {
            PsiFileFactory.getInstance(projectRule.project).createFileFromText(
                YAMLLanguage.INSTANCE, """
Unrecognized:
        """.trimIndent()
            ) as YAMLFile
        }

        val initialAnnotationResults = InitialAnnotationResults(psiFile)

        assertFalse(initialAnnotationResults.isCloudFormationTemplate)
    }

    @Test
    fun isCloudFormationTemplate_Yaml_ResourcesAndType() {
        val psiFile = runInEdtAndGet {
            PsiFileFactory.getInstance(projectRule.project).createFileFromText(
                YAMLLanguage.INSTANCE, """
Resources:
  S3Bucket:
    Type: AWS::S3::Bucket
        """.trimIndent()
            ) as YAMLFile
        }

        val initialAnnotationResults = InitialAnnotationResults(psiFile)

        assertTrue(initialAnnotationResults.isCloudFormationTemplate)
    }

    @Test
    fun isNotCloudFormationTemplate_Yaml_ResourcesButNoType() {
        val psiFile = runInEdtAndGet {
            PsiFileFactory.getInstance(projectRule.project).createFileFromText(
                YAMLLanguage.INSTANCE, """
Resources:
  S3Bucket:
    
        """.trimIndent()
            ) as YAMLFile
        }

        val initialAnnotationResults = InitialAnnotationResults(psiFile)

        assertFalse(initialAnnotationResults.isCloudFormationTemplate)
    }

    @Test
    fun isCloudFormationTemplate_Yaml_HasVersionKey() {
        val psiFile = runInEdtAndGet {
            PsiFileFactory.getInstance(projectRule.project).createFileFromText(
                YAMLLanguage.INSTANCE, """
AWSTemplateFormatVersion: "2010-09-09"
        """.trimIndent()
            ) as YAMLFile
        }

        val initialAnnotationResults = InitialAnnotationResults(psiFile)

        assertTrue(initialAnnotationResults.isCloudFormationTemplate)
    }

    @Test
    fun isNotCloudFormationTemplate_NotJsonOrYaml() {
        val psiFile = runInEdtAndGet {
            PsiFileFactory.getInstance(projectRule.project).createFileFromText(
                XMLLanguage.INSTANCE, """
<demo>test</demo>
        """.trimIndent()
            ) as XmlFile
        }

        val initialAnnotationResults = InitialAnnotationResults(psiFile)

        assertFalse(initialAnnotationResults.isCloudFormationTemplate)
    }
}
