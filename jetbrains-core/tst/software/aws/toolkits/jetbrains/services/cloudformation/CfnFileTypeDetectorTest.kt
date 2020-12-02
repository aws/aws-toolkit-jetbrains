// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.services.cloudformation.json.JsonCfnFileType
import software.aws.toolkits.jetbrains.services.cloudformation.yaml.YamlCfnFileType
import software.aws.toolkits.jetbrains.utils.rules.JavaCodeInsightTestFixtureRule

class CfnFileTypeDetectorTest {
    @Rule
    @JvmField
    val projectRule = JavaCodeInsightTestFixtureRule()

    @Test
    fun jsonFileWithoutCloudFormationContent() {
        assertNotCloudFormationJson(
            content = "{}"
        )
    }

    @Test
    fun jsonFileWithCloudFormationContent() {
        assertCloudFormationJson(
            content = "{ AWSTemplateFormatVersion: '2010-09-09' }"
        )
    }

    @Test
    fun jsonFileWithSamContent() {
        assertCloudFormationJson(
            content = "{ Transform: 'AWS::Serverless-2016-10-31' }"
        )
    }

    @Test
    fun jsonTemplateFileWithCloudFormationContent() {
        assertCloudFormationJson(
            fileName = "test.template",
            content = "{ AWSTemplateFormatVersion: '2010-09-09' }"
        )
    }

    @Test
    fun jsonTemplateFileWithSamContent() {
        assertCloudFormationJson(
            fileName = "test.template",
            content = "{ Transform: 'AWS::Serverless-2016-10-31' }"
        )
    }

    @Test
    fun yamlFileWithoutCloudFormationContent() {
        assertNotCloudFormationYaml(
            content = "Hello: World"
        )
    }

    @Test
    fun yamlFileWithCloudFormationContent() {
        assertCloudFormationYaml(
            content = "AWSTemplateFormatVersion: 2010-09-09"
        )
    }

    @Test
    fun yamlFileWithSamContent() {
        assertCloudFormationYaml(
            content = "Transform: AWS::Serverless-2016-10-31"
        )
    }

    @Test
    fun yamlTemplateFileWithCloudFormationContent() {
        assertCloudFormationYaml(
            fileName = "test.template",
            content = "AWSTemplateFormatVersion: 2010-09-09"
        )
    }

    @Test
    fun yamlTemplateFileWithSamContent() {
        assertCloudFormationYaml(
            fileName = "test.template",
            content = "Transform: AWS::Serverless-2016-10-31"
        )
    }

    private fun assertNotCloudFormationJson(fileName: String = "test.json", content: String) {
        val document = projectRule.fixture.configureByText(fileName, content)
        assertThat(document.virtualFile.fileType).isNotEqualTo(JsonCfnFileType.INSTANCE)
    }

    private fun assertCloudFormationJson(fileName: String = "test.json", content: String) {
        val document = projectRule.fixture.configureByText(fileName, content)
        assertThat(document.virtualFile.fileType).isEqualTo(JsonCfnFileType.INSTANCE)
    }

    private fun assertNotCloudFormationYaml(fileName: String = "test.yaml", content: String) {
        val document = projectRule.fixture.configureByText(fileName, content)
        assertThat(document.virtualFile.fileType).isNotEqualTo(YamlCfnFileType.INSTANCE)
    }

    private fun assertCloudFormationYaml(fileName: String = "test.yaml", content: String) {
        val document = projectRule.fixture.configureByText(fileName, content)
        assertThat(document.virtualFile.fileType).isEqualTo(YamlCfnFileType.INSTANCE)
    }
}
