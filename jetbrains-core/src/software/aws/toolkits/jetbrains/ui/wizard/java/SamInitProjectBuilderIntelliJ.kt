// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ui.wizard.java

import com.intellij.ide.projectWizard.ProjectTemplateList
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.jetbrains.python.module.PythonModuleType
import com.jetbrains.python.sdk.PythonSdkType
import icons.AwsIcons
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.jetbrains.ui.wizard.SAM_TEMPLATES
import software.aws.toolkits.jetbrains.ui.wizard.SamModuleType
import software.aws.toolkits.jetbrains.ui.wizard.SamProjectTemplateWrapper

class SamInitModuleBuilder : ModuleBuilder() {
    var runtime: Runtime = Runtime.UNKNOWN_TO_SDK_VERSION
    lateinit var runtimeSelectionPanel: SamInitRuntimeSelectionPanel
    lateinit var template: SamProjectTemplateWrapper

    /*  Trick IDEA to give us a custom first screen without using the WizardDelegate trick
        described in AndroidModuleBuilder
        https://github.com/JetBrains/android/blob/master/android/src/com/android/tools/idea/npw/ideahost/AndroidModuleBuilder.java
    */
    override fun getModuleType() = SamModuleType.instance

    // we want to use our own custom template selection step
    override fun isTemplateBased() = false

    fun getIdeaModuleType() = when (runtime) {
        Runtime.JAVA8 -> JavaModuleType.getModuleType()
        Runtime.PYTHON2_7, Runtime.PYTHON3_6 -> PythonModuleType.getInstance()
        else -> ModuleType.EMPTY
    }

    fun getSdkType() = when (runtime) {
        Runtime.JAVA8 -> JavaSdk.getInstance()
        Runtime.PYTHON2_7, Runtime.PYTHON3_6 -> PythonSdkType.getInstance()
        else -> JavaSdk.getInstance()
    }

    override fun setupRootModel(rootModel: ModifiableRootModel) {
        if (myJdk != null) {
            rootModel.sdk = myJdk
        } else {
            rootModel.inheritSdk()
        }
        val moduleType = getIdeaModuleType().id
        rootModel.module.setModuleType(moduleType)
        val project = rootModel.project

        template.samProjectTemplate.build(runtime, project.baseDir)
        rootModel.addContentEntry(project.baseDir)
    }

    override fun getPresentableName() = SamModuleType.ID

    override fun getDescription() = SamModuleType.DESCRIPTION

    override fun getNodeIcon() = AwsIcons.Resources.LAMBDA_FUNCTION

    override fun getCustomOptionsStep(context: WizardContext?, parentDisposable: Disposable?): ModuleWizardStep? {
        runtimeSelectionPanel = SamInitRuntimeSelectionPanel(this, context)
        return runtimeSelectionPanel
    }

    override fun createWizardSteps(wizardContext: WizardContext, modulesProvider: ModulesProvider) =
            arrayOf(SamInitTemplateSelectionStep(this, wizardContext))
}

class SamInitTemplateSelectionStep(
    val builder: SamInitModuleBuilder,
    val context: WizardContext
) : ModuleWizardStep() {
    val templateSelectionPanel = ProjectTemplateList()

    init {
        templateSelectionPanel.setTemplates(SAM_TEMPLATES.map { it.getModuleBuilderProjectTemplate(builder) }, true)
    }

    override fun updateDataModel() {
        context.projectBuilder = builder
        builder.template = templateSelectionPanel.selectedTemplate as SamProjectTemplateWrapper
    }

    override fun getComponent() = templateSelectionPanel
}