// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ui.wizard

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ProjectTemplate
import icons.AwsIcons
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.SamInitRunner

class SamProjectTemplateWrapper(
    val samProjectTemplate: SamProjectTemplate,
    val builder: ModuleBuilder
) : ProjectTemplate {
    override fun getIcon() = samProjectTemplate.getIcon()

    override fun getName() = samProjectTemplate.getName()

    override fun getDescription() = samProjectTemplate.getDescription()

    override fun createModuleBuilder() = builder

    override fun validateSettings() = null
}

abstract class SamProjectTemplate {
    abstract fun getName(): String

    open fun getDescription(): String? = null

    override fun toString() = getName()

    fun getIcon() = SamModuleType.ICON

    fun buildCommand(runtime: Runtime, outputDir: VirtualFile) = SamInitRunner().applyRuntime(runtime)
        .applyName(SamModuleType.ID)
        .applyOutputDir(outputDir)

    open fun build(runtime: Runtime, outputDir: VirtualFile) {
        buildCommand(runtime, outputDir).execute()
    }

    fun getModuleBuilderProjectTemplate(builder: ModuleBuilder) =
            SamProjectTemplateWrapper(this, builder)
}

class SamModuleType : ModuleType<SamInitModuleBuilder>(ID) {
    override fun getNodeIcon(p0: Boolean) = ICON

    override fun createModuleBuilder() = SamInitModuleBuilder()

    override fun getName() = "SAM Name"

    override fun getDescription() = "SAM Module Type Description"

    companion object {
        val ICON = AwsIcons.Resources.LAMBDA_FUNCTION
        val ID = "SAM"
        val DESCRIPTION = "AWS Serverless Application Model (AWS SAM) prescribes rules for expressing Serverless applications on AWS."
        val instance = SamModuleType()
    }
}

class SamHelloWorld : SamProjectTemplate() {
    override fun getName() = "AWS SAM Hello World"

    override fun getDescription() = "Hello World Description"
}

class SamDynamoDBCookieCutter : SamProjectTemplate() {
    override fun getName() = "AWS SAM DynamoDB Event Example"

    override fun getDescription() = "Sample SAM Template to interact with DynamoDB Events"

    override fun build(runtime: Runtime, outputDir: VirtualFile) {
        buildCommand(runtime, outputDir)
                .applyLocation("gh:aws-samples/cookiecutter-aws-sam-dynamodb-python")
                .execute()
    }
}

val SAM_TEMPLATES = listOf(SamHelloWorld(), SamDynamoDBCookieCutter())