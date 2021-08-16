// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.lambda.wizard

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.layout.panel
import software.amazon.awssdk.services.lambda.model.PackageType
import software.aws.toolkits.core.lambda.LambdaRuntime
import software.aws.toolkits.jetbrains.core.tools.SemanticVersion
import software.aws.toolkits.jetbrains.core.tools.ToolManager
import software.aws.toolkits.jetbrains.core.tools.ToolPathSelector
import software.aws.toolkits.jetbrains.core.tools.toValidationInfo
import software.aws.toolkits.jetbrains.services.lambda.minSamInitVersion
import software.aws.toolkits.jetbrains.services.lambda.runtimeGroup
import software.aws.toolkits.jetbrains.services.lambda.sam.SamCommon
import software.aws.toolkits.jetbrains.services.lambda.sam.SamCli
import software.aws.toolkits.jetbrains.utils.ui.validationInfo
import software.aws.toolkits.resources.message
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRadioButton

class SamInitSelectionPanel(
    wizardFragmentList: List<WizardFragment>,
    private val projectLocation: TextFieldWithBrowseButton? = null, /* Only available in PyCharm! */
    private val runtimeFilter: (LambdaRuntime) -> Boolean = { true },
    private val wizardUpdateCallback: () -> Unit = {} /* Used in Rider to refresh the validation */
) {
    lateinit var mainPanel: JPanel

    private lateinit var runtimeComboBox: ComboBox<LambdaRuntime>
    private lateinit var packageZip: JRadioButton
    private lateinit var templateComboBox: ComboBox<SamProjectTemplate>
    private lateinit var fragments: Wrapper
    private lateinit var executableSelector: ToolPathSelector<SamCli>
    private lateinit var executableResult: Wrapper

    private val wizardFragments: Map<WizardFragment, JComponent>
    private val runtimes = CollectionComboBoxModel(supportedRuntimes())

    init {
        runtimeComboBox.model = runtimes
        runtimeComboBox.addActionListener {
            runtimeUpdate()
            wizardUpdate()
        }

        templateComboBox.addActionListener { wizardUpdate() }
        templateComboBox.renderer = SimpleListCellRenderer.create { label, value, _ ->
            label.text = value?.displayName()
            label.toolTipText = value?.description()
        }

        packageZip.addChangeListener {
            runtimeUpdate()
        }

        wizardFragments = wizardFragmentList.associateWith {
            val panel = JPanel(BorderLayout())
            val fragmentTitle = it.title()
            if (fragmentTitle != null) {
                panel.border = IdeBorderFactory.createTitledBorder(it.title(), false)
            }
            panel.add(it.component(), BorderLayout.CENTER)
            panel
        }

        fragments.setContent(
            panel {
                wizardFragments.values.forEach {
                    row() {
                        it(grow)
                    }
                }
            }
        )

        // this will also fire wizardUpdate since templateComboBox will change
        // otherwise we make 2 of them
        runtimeUpdate()
    }

    private fun createUIComponents() {
        executableResult = Wrapper()
        executableSelector = ToolPathSelector(SamCli, executableResult).apply { reset() }
    }

    // Source all templates, find all the runtimes they support, then filter those by what the IDE supports
    private fun supportedRuntimes(): MutableList<LambdaRuntime> = SamProjectTemplate.supportedTemplates().asSequence()
        .flatMap {
            when (packageType()) {
                PackageType.ZIP -> it.supportedZipRuntimes().asSequence()
                else -> it.supportedImageRuntimes().asSequence()
            }
        }
        .filter(runtimeFilter)
        .distinct()
        .sorted()
        .toMutableList()

    private fun packageType() = when {
        packageZip.isSelected -> PackageType.ZIP
        else -> PackageType.IMAGE
    }

    fun setRuntime(runtime: LambdaRuntime) {
        runtimeComboBox.selectedItem = runtime
    }

    private fun runtimeUpdate() {
        // Refresh the runtimes list since zip and image differ
        runtimes.removeAll()
        runtimes.add(supportedRuntimes())

        val selectedTemplate = templateComboBox.selectedItem as? SamProjectTemplate
        templateComboBox.removeAllItems()
        val selectedRuntime = runtimes.selected ?: return

        val packagingType = packageType()
        SamProjectTemplate.supportedTemplates().asSequence()
            .filter {
                when (packagingType) {
                    PackageType.ZIP -> it.supportedZipRuntimes().contains(selectedRuntime)
                    else -> it.supportedImageRuntimes().contains(selectedRuntime)
                }
            }
            .forEach { templateComboBox.addItem(it) }

        // repopulate template after runtime updates
        // this should no-op if the previous selection is not applicable to the current runtime
        if (selectedTemplate != null) {
            templateComboBox.selectedItem = selectedTemplate
        }
    }

    /**
     * Updates UI fragments in the wizard after a combobox update
     */
    private fun wizardUpdate() {
        val selectedRuntime = runtimes.selected
        val selectedTemplate = templateComboBox.selectedItem as? SamProjectTemplate
        wizardFragments.forEach { (wizardFragment, jComponent) ->
            val isApplicable = wizardFragment.isApplicable(selectedTemplate)
            if (isApplicable) {
                wizardFragment.updateUi(projectLocation, selectedRuntime?.runtimeGroup, selectedTemplate)
            }
            jComponent.isVisible = isApplicable
        }
        wizardUpdateCallback()
    }

    fun validate(): ValidationInfo? {
        val selectedRuntime = runtimes.selected ?: return templateComboBox.validationInfo(message("sam.init.error.no.runtime.selected"))

        executableSelector.apply()

        val toolManager = ToolManager.getInstance()
        val samExecutable2 = toolManager.getExecutable(SamCli)

        val stricterMinVersion = listOfNotNull(
            if (packageType() == PackageType.IMAGE) SamCommon.minImageVersion else null,
            selectedRuntime.minSamInitVersion()
        ).max()?.let {
            SemanticVersion(it)
        }

        val validity = toolManager.validateCompatability(
            project = null,
            tool = samExecutable2,
            stricterMinVersion = stricterMinVersion
        )

        validity.toValidationInfo(SamCli)?.let {
            return it
        }

        val samProjectTemplate = templateComboBox.selectedItem as? SamProjectTemplate
            ?: return templateComboBox.validationInfo(message("sam.init.error.no.template.selected"))

        return wizardFragments.keys
            .filter { it.isApplicable(samProjectTemplate) }
            .mapNotNull { it.validateFragment() }
            .firstOrNull()
    }

    fun getNewProjectSettings(): SamNewProjectSettings {
        val lambdaRuntime = runtimes.selected
            ?: throw RuntimeException("No Runtime is supported in this Platform.")
        val samProjectTemplate = templateComboBox.selectedItem as? SamProjectTemplate
            ?: throw RuntimeException("No SAM template is supported for this runtime: $lambdaRuntime")

        return SamNewProjectSettings(template = samProjectTemplate, runtime = lambdaRuntime, packagingType = packageType())
    }
}
