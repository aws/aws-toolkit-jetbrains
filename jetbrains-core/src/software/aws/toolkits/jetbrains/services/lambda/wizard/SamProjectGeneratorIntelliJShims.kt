// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.wizard

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.SettingsStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.platform.ProjectTemplatesFactory
import icons.AwsIcons
import software.aws.toolkits.jetbrains.services.lambda.runtimeGroup
import software.aws.toolkits.resources.message

// Meshing of two worlds. IntelliJ wants validation errors to be thrown exceptions. Non-IntelliJ wants validation errors
// to be returned as a ValidationInfo object. We have a shim to convert thrown exceptions into objects,
// but then we lose the ability in IntelliJ to fail validation without showing an error. This is a workaround for that case.
class ValidationException : Exception()

// IntelliJ shim requires a ModuleBuilder
// UI is centralized in generator and is passed in to have access to UI elements
// TODO: Kill this, it doesnt need to be tied to a module builder?
class SamProjectBuilder(private val generator: SamProjectGenerator) : ModuleBuilder() {
    // hide this from the new project menu
    override fun isAvailable() = false

    // dummy type to fulfill the interface, will be replaced in setupRootModel()
    override fun getModuleType(): ModuleType<*>? = ModuleType.EMPTY

    // IntelliJ create commit step
    override fun setupRootModel(rootModel: ModifiableRootModel) {
        val settings = generator.peer.settings

        settings.template.setupSdk(rootModel, settings)

        // Set module type
        val selectedRuntime = settings.runtime
        val moduleType = selectedRuntime.runtimeGroup?.getModuleType() ?: ModuleType.EMPTY
        rootModel.module.setModuleType(moduleType.id)

        val contentEntry = doAddContentEntry(rootModel) ?: throw Exception(message("sam.init.error.no.project.basepath"))
        val outputDir = contentEntry.file ?: throw Exception(message("sam.init.error.no.virtual.file"))

        // ModifiableRootModel takes a final ProjectRootManagerImpl which has a final project, so we have guaranteed access to project here
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(rootModel.project, message("sam.init.generating.template"), false) {
                override fun run(indicator: ProgressIndicator) {
                    ModuleRootModificationUtil.updateModel(rootModel.module) { model ->
                        val samTemplate = settings.template
                        samTemplate.build(project, rootModel.module.name, selectedRuntime, null, outputDir)

                        generator.wizardFragments.forEach { it.postProjectGeneration(model, indicator) }

                        // TODO: Rip the following out of the template class
                        samTemplate.postCreationAction(settings, outputDir, model, indicator)
                    }
                }
            }
        )
    }

    override fun modifySettingsStep(settingsStep: SettingsStep): ModuleWizardStep? {
        generator.peer.buildUI(settingsStep)

        // need to return an object with validate() implemented for validation
        return object : ModuleWizardStep() {
            override fun getComponent() = null

            override fun updateDataModel() {}

            @Throws(ConfigurationException::class)
            override fun validate(): Boolean {
                try {
                    val info = generator.peer.validate()
                    if (info != null) throw ConfigurationException(info.message)
                } catch (_: ValidationException) {
                    return false
                }

                return true
            }
        }
    }
}

class SamProjectGeneratorIntelliJAdapter : ProjectTemplatesFactory() {
    override fun createTemplates(group: String?, context: WizardContext) = arrayOf(SamProjectGenerator())

    override fun getGroupIcon(group: String?) = AwsIcons.Logos.AWS

    override fun getGroups() = arrayOf("AWS")
}
