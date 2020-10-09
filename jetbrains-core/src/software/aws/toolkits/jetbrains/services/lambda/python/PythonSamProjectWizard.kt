// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.python

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PlatformUtils
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.jetbrains.services.lambda.BuiltInRuntimeGroups
import software.aws.toolkits.jetbrains.services.lambda.sam.SamCommon
import software.aws.toolkits.jetbrains.services.lambda.wizard.IntelliJSdkSelectionPanel
import software.aws.toolkits.jetbrains.services.lambda.wizard.SamNewProjectSettings
import software.aws.toolkits.jetbrains.services.lambda.wizard.SamProjectTemplate
import software.aws.toolkits.jetbrains.services.lambda.wizard.SamProjectWizard
import software.aws.toolkits.jetbrains.services.lambda.wizard.SdkSelector
import software.aws.toolkits.jetbrains.services.lambda.wizard.TemplateParameters
import software.aws.toolkits.jetbrains.services.lambda.wizard.TemplateParameters.AppBasedTemplate
import software.aws.toolkits.jetbrains.services.lambda.wizard.TemplateParameters.LocationBasedTemplate
import software.aws.toolkits.resources.message

class PythonSamProjectWizard : SamProjectWizard {
    override fun createSdkSelectionPanel(projectLocation: TextFieldWithBrowseButton?): SdkSelector? = when {
        PlatformUtils.isPyCharm() -> PyCharmSdkSelectionPanel(projectLocation)
        else -> IntelliJSdkSelectionPanel(BuiltInRuntimeGroups.Python)
    }

    override fun listTemplates(): Collection<SamProjectTemplate> = listOf(
        SamHelloWorldPython(),
        SamDynamoDBCookieCutter(),
        SamEventBridgeHelloWorld(),
        SamEventBridgeStarterApp()
    )
}

abstract class PythonSamProjectTemplate : SamProjectTemplate() {
    override fun supportedRuntimes() = setOf(Runtime.PYTHON2_7, Runtime.PYTHON3_6, Runtime.PYTHON3_7, Runtime.PYTHON3_8)

    override fun postCreationAction(
        settings: SamNewProjectSettings,
        contentRoot: VirtualFile,
        rootModel: ModifiableRootModel,
        indicator: ProgressIndicator
    ) {
        super.postCreationAction(settings, contentRoot, rootModel, indicator)
        SamCommon.setSourceRoots(contentRoot, rootModel.project, rootModel)
    }
}

class SamHelloWorldPython : PythonSamProjectTemplate() {
    override fun getName() = message("sam.init.template.hello_world.name")

    override fun getDescription() = message("sam.init.template.hello_world.description")

    override fun templateParameters(): TemplateParameters = AppBasedTemplate("hello-world", "pip")
}

class SamDynamoDBCookieCutter : PythonSamProjectTemplate() {
    override fun getName() = message("sam.init.template.dynamodb_cookiecutter.name")

    override fun getDescription() = message("sam.init.template.dynamodb_cookiecutter.description")

    override fun templateParameters(): TemplateParameters = LocationBasedTemplate("gh:aws-samples/cookiecutter-aws-sam-dynamodb-python")
}

class SamEventBridgeHelloWorld : PythonSamProjectTemplate() {
    override fun supportedRuntimes() = setOf(Runtime.PYTHON3_6, Runtime.PYTHON3_7, Runtime.PYTHON3_8)

    override fun getName() = message("sam.init.template.eventBridge_helloWorld.name")

    override fun getDescription() = message("sam.init.template.eventBridge_helloWorld.description")

    override fun templateParameters(): TemplateParameters = AppBasedTemplate("eventBridge-hello-world", "pip")
}

class SamEventBridgeStarterApp : PythonSamProjectTemplate() {
    override fun supportedRuntimes() = setOf(Runtime.PYTHON3_6, Runtime.PYTHON3_7, Runtime.PYTHON3_8)

    override fun getName() = message("sam.init.template.eventBridge_starterApp.name")

    override fun getDescription() = message("sam.init.template.eventBridge_starterApp.description")

    override fun templateParameters(): TemplateParameters = AppBasedTemplate("eventBridge-schema-app", "pip")

    override fun supportsDynamicSchemas(): Boolean = true

    override fun postCreationAction(
        settings: SamNewProjectSettings,
        contentRoot: VirtualFile,
        rootModel: ModifiableRootModel,
        indicator: ProgressIndicator
    ) {
//        settings.schemaParameters?.let {
//            val functionRoot = Paths.get(contentRoot.path, functionName())
//
//            SamSchemaDownloadPostCreationAction().downloadCodeIntoWorkspace(
//                it,
//                contentRoot,
//                functionRoot,
//                SchemaCodeLangs.PYTHON3_6,
//                rootModel.project,
//                indicator
//            )
//        }

        super.postCreationAction(settings, contentRoot, rootModel, indicator)
    }
}
