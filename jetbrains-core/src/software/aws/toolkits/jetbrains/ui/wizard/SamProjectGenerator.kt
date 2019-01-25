// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ui.wizard

import com.intellij.ide.util.projectWizard.AbstractNewProjectStep
import com.intellij.ide.util.projectWizard.CustomStepProjectGenerator
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase
import com.intellij.ide.util.projectWizard.SettingsStep
import com.intellij.ide.util.projectWizard.WebProjectTemplate
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.welcomeScreen.AbstractActionWithPanel
import com.intellij.platform.DirectoryProjectGenerator
import com.intellij.platform.HideableProjectGenerator
import com.intellij.platform.ProjectGeneratorPeer
import com.intellij.platform.ProjectTemplate
import com.jetbrains.python.sdk.PyLazySdk
import icons.AwsIcons
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.SamCommon
import software.aws.toolkits.jetbrains.services.lambda.python.PythonRuntimeGroup
import software.aws.toolkits.resources.message
import javax.swing.Icon
import javax.swing.JComponent

class SamNewProjectSettings {
    lateinit var runtime: Runtime // this is only used in IntelliJ
    lateinit var template: SamProjectTemplate
}

val NOOP_CALLBACK = object : AbstractNewProjectStep.AbstractCallback<SamNewProjectSettings>() {}

// ref: https://github.com/JetBrains/intellij-plugins/blob/master/vuejs/src/org/jetbrains/vuejs/cli/VueCliProjectGenerator.kt
class SamProjectGenerator : ProjectTemplate,
                            WebProjectTemplate<SamNewProjectSettings>(), // pycharm hack
                            DirectoryProjectGenerator<SamNewProjectSettings>,
                            CustomStepProjectGenerator<SamNewProjectSettings>,
                            HideableProjectGenerator {
    val settings = SamNewProjectSettings()
    val step = SamProjectRuntimeSelectionStep(this, NOOP_CALLBACK)
    val peer = SamProjectGeneratorSettingsPeer(this)

    override fun isHidden(): Boolean = false

    // steps are used by non-IntelliJ IDEs
    override fun createStep(projectGenerator: DirectoryProjectGenerator<SamNewProjectSettings>?, callback: AbstractNewProjectStep.AbstractCallback<SamNewProjectSettings>?): AbstractActionWithPanel = step

    // non-IntelliJ project commit step
    override fun generateProject(project: Project, baseDir: VirtualFile, settings: SamNewProjectSettings, module: Module) {
        // first figure out if we even have an sdk...
        var sdk = peer.sdkPanel.getSdk()
        if (sdk is PyLazySdk) {
            val createdSdk = sdk.create()
            if (createdSdk != null) {
                SdkConfigurationUtil.addSdk(createdSdk)
                sdk = createdSdk
            }
        }

        val runtime = PythonRuntimeGroup.determineRuntimeForSdk(sdk
            ?: throw RuntimeException(message("sam.init.python.bad_sdk"))
        ) ?: throw RuntimeException("Could not determine runtime for SDK")

        // set the project sdk
        runInEdt {
            runWriteAction {
                ProjectRootManager.getInstance(project).projectSdk = sdk
            }
        }

        val template = settings.template
        template.build(runtime, baseDir)

        runInEdt {
            runWriteAction {
                val rootModel = ModuleRootManager.getInstance(module).modifiableModel
                SamCommon.excludeSamDirectory(baseDir, rootModel)
                SamCommon.setSourceRoots(baseDir, project, rootModel)

                rootModel.commit()
            }
        }
    }

    // the peer is in control of the first pane
    override fun createPeer(): ProjectGeneratorPeer<SamNewProjectSettings> = peer

    // these overrides will give us a section for non-IntelliJ IDEs
    override fun getName() = message("sam.init.name")

    override fun getDescription(): String? = message("sam.init.description")

    override fun getLogo(): Icon = AwsIcons.Resources.SERVERLESS_APP

    override fun getIcon(): Icon = logo

    override fun createModuleBuilder(): ModuleBuilder = AwsModuleBuilder(this)

    // force the initial validation
    override fun postponeValidation(): Boolean = false

    // validation is done in the peer
    override fun validateSettings(): ValidationInfo? = null
}

// non-IntelliJ step UI
class SamProjectRuntimeSelectionStep(
    private val projectGenerator: SamProjectGenerator,
    callback: AbstractNewProjectStep.AbstractCallback<SamNewProjectSettings>
) : ProjectSettingsStepBase<SamNewProjectSettings>(projectGenerator, callback) {
    fun getLocationField(): TextFieldWithBrowseButton = myLocationField
    private fun getGeneratorPeer(): SamProjectGeneratorSettingsPeer = (projectGenerator.step.peer as SamProjectGeneratorSettingsPeer)

    override fun registerValidators() {
        super.registerValidators()
        getGeneratorPeer().registerValidators()
    }
}

class SamProjectGeneratorSettingsPeer(private val generator: SamProjectGenerator) : ProjectGeneratorPeer<SamNewProjectSettings> {
    val sdkPanel: SdkSelectionPanelImpl by lazy { SdkSelectionPanelImpl(NOOP_CALLBACK, generator) }

    // need a listener to autorun this? this hook is used in PyCharm
    override fun validate(): ValidationInfo? {
        val validationErrors = sdkPanel.validateAll()
        return validationErrors?.firstOrNull()
    }

    override fun getSettings(): SamNewProjectSettings = generator.settings

    // "Deprecated" but required to implement. Not importing to avoid the import deprecation warning.
    @Suppress("OverridingDeprecatedMember", "DEPRECATION")
    override fun addSettingsStateListener(listener: com.intellij.platform.WebProjectGenerator.SettingsStateListener) {}

    private val templateComboBox = ComboBox<SamProjectTemplate>()
    // we sacrifice a lot of convenience so we can build the UI here...
    override fun buildUI(settingsStep: SettingsStep) {
        // delegate to another panel instead of trying to write UI as code
        val panel = SamInitSelectionPanel(settings)
        sdkPanel.transformUI(panel)

        settingsStep.addSettingsComponent(panel.mainPanel)
    }

    // order matters! we build the peer UI before we build the step UI,
    // so validators should be done after BOTH have been constructed
    fun registerValidators() {
        // register any IDE-specific behavior
        // sdk selector validation
        sdkPanel.registerListeners()

        // generic
        // register changes into our settings holder
        templateComboBox.addItemListener { settings.template = (it.item as SamProjectTemplate) }
    }

    override fun isBackgroundJobRunning(): Boolean = false

    override fun getComponent(): JComponent = sdkPanel.sdkSelectionPanel
}