// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.wizard.AbstractWizard
import com.intellij.ide.wizard.StepAdapter
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.CheckBoxList
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import software.aws.toolkits.jetbrains.services.cfnlsp.documents.CfnDocumentManager
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DeploymentMode
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.Parameter
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceToImport
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.Tag
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.TemplateParameter
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.TemplateResource
import software.aws.toolkits.jetbrains.services.cfnlsp.server.CFN_SUPPORTED_EXTENSIONS
import software.aws.toolkits.resources.AwsToolkitBundle.message
import java.awt.BorderLayout
import java.io.File
import java.net.URI
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.table.AbstractTableModel

internal data class TemplateItem(
    val displayName: String,
    val uri: String?,
) {
    override fun toString() = displayName
}

internal data class ValidateAndDeploySettings(
    val templatePath: String,
    val stackName: String,
    val s3Bucket: String?,
    val s3Key: String?,
    val parameters: List<Parameter>,
    val capabilities: List<String>,
    val tags: List<Tag>,
    val onStackFailure: String?,
    val includeNestedStacks: Boolean,
    val importExistingResources: Boolean,
    val deploymentMode: DeploymentMode?,
    val resourcesToImport: List<ResourceToImport>?,
)

internal class ValidateAndDeployWizard(
    project: Project,
    documentManager: CfnDocumentManager,
    prefilledTemplatePath: String? = null,
    prefilledStackName: String? = null,
    templateParameters: List<TemplateParameter> = emptyList(),
    detectedCapabilities: List<String> = emptyList(),
    existingParameters: List<Parameter>? = null,
    existingTags: List<Tag>? = null,
    hasArtifacts: Boolean = false,
    templateResources: List<TemplateResource> = emptyList(),
    isExistingStack: Boolean = false,
) : AbstractWizard<StepAdapter>(message("cloudformation.deploy.dialog.title"), project) {

    private val configStep = ConfigurationStep(
        project, documentManager, prefilledTemplatePath, prefilledStackName,
        templateParameters, detectedCapabilities, existingParameters, existingTags,
        hasArtifacts, templateResources, isExistingStack,
    )
    private val importStep = ImportResourcesStep(templateResources)

    init {
        addStep(configStep)
        if (templateResources.isNotEmpty()) {
            addStep(importStep)
        }
        configStep.setImportToggleListener { updateWizardButtons() }
        init()
    }

    override fun getHelpId(): String? = null

    override fun helpAction() {
        BrowserUtil.browse(HELP_URL)
    }

    override fun doHelpAction() {
        BrowserUtil.browse(HELP_URL)
    }

    override fun updateButtons() {
        super.updateButtons()
        if (isLastStep() || !configStep.isImportSelected()) {
            nextButton.text = "Create Change Set"
        }
    }

    override fun canGoNext(): Boolean {
        if (currentStep == 0 && !configStep.isImportSelected()) return true
        return super.canGoNext()
    }

    override fun doNextAction() {
        if (currentStep == 0) {
            val error = configStep.validate()
            if (error != null) {
                setErrorText(error)
                return
            }
            setErrorText(null)
        }
        super.doNextAction()
    }

    override fun doOKAction() {
        if (currentStep == 0) {
            val error = configStep.validate()
            if (error != null) {
                setErrorText(error)
                return
            }
        }
        if (currentStep == 1 || (currentStep == 0 && configStep.isImportSelected())) {
            val error = importStep.validate()
            if (error != null) {
                setErrorText(error)
                return
            }
        }
        super.doOKAction()
    }

    override fun isLastStep(): Boolean {
        if (currentStep == 0 && !configStep.isImportSelected()) return true
        return currentStep == stepCount - 1
    }

    fun getSettings(): ValidateAndDeploySettings {
        configStep.saveState()
        return ValidateAndDeploySettings(
            templatePath = configStep.getTemplatePath(),
            stackName = configStep.getStackName(),
            s3Bucket = configStep.getS3Bucket(),
            s3Key = configStep.getS3Key(),
            parameters = configStep.getParameters(),
            capabilities = configStep.getCapabilities(),
            tags = configStep.getTags(),
            onStackFailure = configStep.getOnStackFailure(),
            includeNestedStacks = configStep.getIncludeNestedStacks(),
            importExistingResources = configStep.isImportSelected(),
            deploymentMode = configStep.getDeploymentMode(),
            resourcesToImport = if (configStep.isImportSelected()) importStep.getResourcesToImport() else null,
        )
    }

    companion object {
        private const val HELP_URL = "https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-changesets-create.html"
    }
}

// Step 1: Main configuration
private class ConfigurationStep(
    project: Project,
    private val documentManager: CfnDocumentManager,
    prefilledTemplatePath: String?,
    prefilledStackName: String?,
    templateParameters: List<TemplateParameter>,
    detectedCapabilities: List<String>,
    existingParameters: List<Parameter>?,
    existingTags: List<Tag>?,
    private val hasArtifacts: Boolean,
    templateResources: List<TemplateResource>,
    private val isExistingStack: Boolean,
) : StepAdapter() {

    private val persistence = ValidateAndDeployPersistence.getInstance(project)
    private val savedState = persistence.state

    private val descriptor = FileChooserDescriptor(true, false, false, false, false, false).withFileFilter {
        it.extension?.lowercase() in CFN_SUPPORTED_EXTENSIONS
    }

    private val templateDropdown = ComboBox<TemplateItem>().apply {
        renderer = SimpleListCellRenderer.create { label, item, _ ->
            label.text = item.displayName
            label.toolTipText = item.uri?.let { URI(it).path }
        }
        addActionListener { toolTipText = getSelectedTooltip() }
    }

    private val browseButton = JButton().apply {
        icon = AllIcons.General.OpenDisk
        addActionListener {
            val selectedFile = FileChooser.chooseFile(descriptor, project, null)
            selectedFile?.let { addFileToDropdown(it.path) }
        }
        toolTipText = "Browse for CloudFormation template"
        margin = JBUI.emptyInsets()
        val iconSize = icon.iconWidth + 24
        preferredSize = JBUI.size(iconSize, preferredSize.height)
        minimumSize = JBUI.size(iconSize, minimumSize.height)
        maximumSize = JBUI.size(iconSize, maximumSize.height)
    }

    private val stackNameField = JBTextField().apply {
        text = prefilledStackName ?: savedState.lastStackName ?: ""
        emptyText.text = message("cloudformation.deploy.dialog.stack_name.placeholder")
    }

    private val s3BucketField = JBTextField().apply {
        text = savedState.s3Bucket ?: ""
        emptyText.text = "S3 bucket name (optional)"
    }

    private val s3KeyField = JBTextField().apply {
        val defaultKey = if (prefilledTemplatePath != null) {
            val f = File(prefilledTemplatePath)
            "${f.nameWithoutExtension}-${System.currentTimeMillis()}.${f.extension}"
        } else {
            null
        }
        text = savedState.s3Key ?: ""
        emptyText.text = defaultKey ?: "S3 object key (optional)"
    }

    private val parameterFields = templateParameters.map { param ->
        val prefill = existingParameters?.find { it.parameterKey == param.name }?.parameterValue
            ?: param.default?.toString() ?: ""
        param to JBTextField().apply {
            text = prefill
            emptyText.text = param.description ?: param.type ?: "String"
        }
    }

    private val capabilityIam = JBCheckBox("CAPABILITY_IAM").apply {
        isSelected = "CAPABILITY_IAM" in detectedCapabilities || savedState.capabilities?.contains("CAPABILITY_IAM") == true
    }
    private val capabilityNamedIam = JBCheckBox("CAPABILITY_NAMED_IAM").apply {
        isSelected = "CAPABILITY_NAMED_IAM" in detectedCapabilities || savedState.capabilities?.contains("CAPABILITY_NAMED_IAM") == true
    }
    private val capabilityAutoExpand = JBCheckBox("CAPABILITY_AUTO_EXPAND").apply {
        isSelected = "CAPABILITY_AUTO_EXPAND" in detectedCapabilities || savedState.capabilities?.contains("CAPABILITY_AUTO_EXPAND") == true
    }

    private val tagsField = JBTextField().apply {
        val existingTagStr = existingTags?.joinToString(",") { "${it.key}=${it.value}" }
        text = existingTagStr ?: savedState.tags ?: ""
        emptyText.text = "key1=value1,key2=value2 (optional)"
    }

    private val onStackFailureCombo = ComboBox(DefaultComboBoxModel(arrayOf("DO_NOTHING", "ROLLBACK", "DELETE"))).apply {
        selectedItem = savedState.onStackFailure ?: "DO_NOTHING"
    }

    private val includeNestedStacksCheckbox = JBCheckBox("Include nested stacks").apply {
        isSelected = savedState.includeNestedStacks
    }

    private val deploymentModeCombo = ComboBox(DefaultComboBoxModel(arrayOf("Standard", "Revert Drift"))).apply {
        selectedItem = "Standard"
        isEnabled = isExistingStack
    }

    private val importResourcesCheckbox = JBCheckBox("Import existing resources")

    private var onImportToggled: (() -> Unit)? = null

    init {
        populateTemplateDropdown()
        prefilledTemplatePath?.let { addFileToDropdown(it) }
        templateDropdown.toolTipText = getSelectedTooltip()
    }

    private fun getSelectedTooltip(): String? {
        val selectedItem = templateDropdown.selectedItem as? TemplateItem
        return selectedItem?.uri?.let { URI(it).path }
    }

    private fun populateTemplateDropdown() {
        val templates = documentManager.getValidTemplates()
        templateDropdown.removeAllItems()

        if (templates.isEmpty()) {
            templateDropdown.addItem(TemplateItem("Click browse button to select template", null))
        } else {
            templates.sortedBy { it.fileName }.forEach { template ->
                templateDropdown.addItem(TemplateItem(template.fileName, template.uri))
            }
        }
    }

    private fun addFileToDropdown(filePath: String) {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) return

        val fileUri = "file://$filePath"

        val existingItem = (0 until templateDropdown.itemCount)
            .map { templateDropdown.getItemAt(it) }
            .find { it.uri == fileUri }

        if (existingItem != null) {
            templateDropdown.selectedItem = existingItem
        } else {
            val newItem = TemplateItem(file.name, fileUri)
            templateDropdown.addItem(newItem)
            templateDropdown.selectedItem = newItem
        }
        templateDropdown.toolTipText = getSelectedTooltip()
    }

    private val component = panel {
        group("Template & Stack") {
            row(message("cloudformation.deploy.dialog.template.label")) {
                cell(templateDropdown).align(Align.FILL).resizableColumn()
                cell(browseButton).align(AlignX.LEFT)
            }
            row(message("cloudformation.deploy.dialog.stack_name.label")) {
                cell(stackNameField).align(Align.FILL)
            }
        }
        if (hasArtifacts || s3BucketField.text.isNotBlank()) {
            group("S3 Upload") {
                row("Bucket:") { cell(s3BucketField).align(Align.FILL) }
                row("Key:") { cell(s3KeyField).align(Align.FILL) }
            }
        } else {
            collapsibleGroup("S3 Upload") {
                row("Bucket:") { cell(s3BucketField).align(Align.FILL) }
                row("Key:") { cell(s3KeyField).align(Align.FILL) }
            }
        }
        if (parameterFields.isNotEmpty()) {
            group("Parameters") {
                parameterFields.forEach { (param, field) ->
                    val label = if (param.allowedValues != null) {
                        "${param.name} (${param.allowedValues.joinToString(", ")}):"
                    } else {
                        "${param.name}:"
                    }
                    row(label) { cell(field).align(Align.FILL) }
                }
            }
        }
        group("Capabilities") {
            row { cell(capabilityIam) }
            row { cell(capabilityNamedIam) }
            row { cell(capabilityAutoExpand) }
        }
        if (templateResources.isNotEmpty()) {
            group("Resource Import") {
                row { cell(importResourcesCheckbox) }
            }
        }
        collapsibleGroup("Advanced Options") {
            row("Tags:") { cell(tagsField).align(Align.FILL) }
            row("On stack failure:") { cell(onStackFailureCombo) }
            if (isExistingStack) {
                row("Deployment mode:") { cell(deploymentModeCombo) }
            }
            row { cell(includeNestedStacksCheckbox) }
        }
    }.apply {
        preferredSize = JBUI.size(550, 550)
    }

    override fun getComponent(): JComponent = component

    fun setImportToggleListener(listener: () -> Unit) {
        onImportToggled = listener
        importResourcesCheckbox.addActionListener { listener() }
    }

    fun isImportSelected(): Boolean = importResourcesCheckbox.isSelected

    fun validate(): String? {
        val selectedItem = templateDropdown.selectedItem as? TemplateItem
        val uri = selectedItem?.uri
            ?: return message("cloudformation.deploy.dialog.template.required")

        val file = File(URI(uri))
        if (!file.isFile) return message("cloudformation.deploy.dialog.template.not_found")
        if (file.extension.lowercase() !in CFN_SUPPORTED_EXTENSIONS) return message("cloudformation.deploy.dialog.template.invalid_extension")

        val name = stackNameField.text.trim()
        if (name.isBlank()) return message("cloudformation.deploy.dialog.stack_name.required")
        if (name.length > 128) return message("cloudformation.deploy.dialog.stack_name.too_long")
        if (!STACK_NAME_PATTERN.matches(name)) return message("cloudformation.deploy.dialog.stack_name.invalid")

        if (hasArtifacts && s3BucketField.text.isBlank()) return "S3 bucket is required because template contains artifacts"

        val tags = tagsField.text.trim()
        if (tags.isNotBlank() && !TAGS_PATTERN.matches(tags)) return "Tags format: key1=value1,key2=value2"

        for ((param, field) in parameterFields) {
            validateParameter(field.text.trim(), param)?.let { return it }
        }
        return null
    }

    private fun validateParameter(value: String, param: TemplateParameter): String? {
        val actual = value.ifBlank { param.default?.toString() ?: "" }
        if (actual.isBlank()) return "${param.name}: Value is required"
        if (param.allowedValues != null && actual !in param.allowedValues.map { it.toString() }) {
            return "${param.name}: Must be one of: ${param.allowedValues.joinToString(", ")}"
        }
        if (param.allowedPattern != null && !Regex(param.allowedPattern).matches(actual)) {
            return "${param.name}: Must match pattern: ${param.allowedPattern}"
        }
        if (param.minLength != null && actual.length < param.minLength) return "${param.name}: Min length: ${param.minLength}"
        if (param.maxLength != null && actual.length > param.maxLength) return "${param.name}: Max length: ${param.maxLength}"
        if (param.type == "Number") {
            val num = actual.toDoubleOrNull() ?: return "${param.name}: Must be a number"
            if (param.minValue != null && num < param.minValue.toDouble()) return "${param.name}: Min value: ${param.minValue}"
            if (param.maxValue != null && num > param.maxValue.toDouble()) return "${param.name}: Max value: ${param.maxValue}"
        }
        return null
    }

    fun saveState() {
        val state = persistence.state
        val selectedItem = templateDropdown.selectedItem as? TemplateItem
        state.lastTemplatePath = selectedItem?.uri?.let { URI(it).path }
        state.lastStackName = stackNameField.text.trim()
        state.s3Bucket = s3BucketField.text.trim().ifBlank { null }
        state.s3Key = s3KeyField.text.trim().ifBlank { null }
        state.onStackFailure = onStackFailureCombo.selectedItem as? String
        state.includeNestedStacks = includeNestedStacksCheckbox.isSelected
        state.importExistingResources = importResourcesCheckbox.isSelected
        state.tags = tagsField.text.trim().ifBlank { null }
        val caps = mutableListOf<String>()
        if (capabilityIam.isSelected) caps.add("CAPABILITY_IAM")
        if (capabilityNamedIam.isSelected) caps.add("CAPABILITY_NAMED_IAM")
        if (capabilityAutoExpand.isSelected) caps.add("CAPABILITY_AUTO_EXPAND")
        state.capabilities = caps.joinToString(",").ifBlank { null }
    }

    fun getTemplatePath(): String {
        val selectedItem = templateDropdown.selectedItem as? TemplateItem
        return selectedItem?.uri?.let { URI(it).path } ?: ""
    }

    fun getStackName(): String = stackNameField.text.trim()
    fun getS3Bucket(): String? = s3BucketField.text.trim().ifBlank { null }
    fun getS3Key(): String? = s3KeyField.text.trim().ifBlank {
        if (s3BucketField.text.isNotBlank()) {
            val path = getTemplatePath()
            if (path.isNotBlank()) {
                val f = File(path)
                "${f.nameWithoutExtension}-${System.currentTimeMillis()}.${f.extension}"
            } else {
                null
            }
        } else {
            null
        }
    }
    fun getParameters(): List<Parameter> = parameterFields.map { (param, field) ->
        Parameter(param.name, field.text.trim().ifBlank { param.default?.toString() ?: "" })
    }
    fun getCapabilities(): List<String> = mutableListOf<String>().apply {
        if (capabilityIam.isSelected) add("CAPABILITY_IAM")
        if (capabilityNamedIam.isSelected) add("CAPABILITY_NAMED_IAM")
        if (capabilityAutoExpand.isSelected) add("CAPABILITY_AUTO_EXPAND")
    }
    fun getTags(): List<Tag> = parseTags(tagsField.text.trim())
    fun getOnStackFailure(): String? = onStackFailureCombo.selectedItem as? String
    fun getIncludeNestedStacks(): Boolean = includeNestedStacksCheckbox.isSelected
    fun getDeploymentMode(): DeploymentMode? =
        if (isExistingStack && deploymentModeCombo.selectedItem == "Revert Drift") DeploymentMode.REVERT_DRIFT else null

    private fun parseTags(input: String): List<Tag> {
        if (input.isBlank()) return emptyList()
        return input.split(",").mapNotNull { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) Tag(parts[0].trim(), parts[1].trim()) else null
        }
    }

    companion object {
        private val STACK_NAME_PATTERN = Regex("^[a-zA-Z][-a-zA-Z0-9]*$")
        private val TAGS_PATTERN = Regex("^[^=,]+=[^=,]+(,[^=,]+=[^=,]+)*$")
    }
}

// Step 2: Import resources
private class ImportResourcesStep(
    private val templateResources: List<TemplateResource>,
) : StepAdapter() {

    private val resourceCheckboxList = CheckBoxList<TemplateResource>().apply {
        templateResources.forEach { addItem(it, "${it.logicalId} (${it.type})", false) }
    }

    private val identifierTableModel = IdentifierTableModel()
    private val identifierTable = JBTable(identifierTableModel).apply {
        setShowGrid(true)
    }

    private val component = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        val topPanel = panel {
            group("Select Resources to Import") {
                row {
                    cell(
                        JBScrollPane(resourceCheckboxList).apply {
                            preferredSize = JBUI.size(530, 150)
                        }
                    ).align(Align.FILL)
                }
            }
        }

        val bottomPanel = panel {
            group("Resource Identifiers") {
                row {
                    cell(
                        JBScrollPane(identifierTable).apply {
                            preferredSize = JBUI.size(530, 200)
                        }
                    ).align(Align.FILL)
                }
                row {
                    comment("Enter the physical identifier for each selected resource's primary key")
                }
            }
        }

        add(topPanel, BorderLayout.NORTH)
        add(bottomPanel, BorderLayout.CENTER)
        preferredSize = JBUI.size(550, 450)

        resourceCheckboxList.setCheckBoxListListener { _, _ -> refreshIdentifierTable() }
    }

    private fun refreshIdentifierTable() {
        val selected = templateResources.filter { resourceCheckboxList.isItemSelected(it) }
        identifierTableModel.updateResources(selected)
    }

    override fun getComponent(): JComponent = component

    fun getResourcesToImport(): List<ResourceToImport>? {
        val imports = identifierTableModel.getResourcesToImport()
        return imports.ifEmpty { null }
    }

    fun validate(): String? {
        val blankResources = identifierTableModel.getResourcesWithBlankIdentifiers()
        if (blankResources.isNotEmpty()) {
            return "Missing identifiers for: ${blankResources.joinToString(", ")}"
        }
        return null
    }
}

private class IdentifierTableModel : AbstractTableModel() {
    private val columns = arrayOf("Logical ID", "Type", "Identifier Key", "Identifier Value")
    private val rows = mutableListOf<IdentifierRow>()

    data class IdentifierRow(
        val logicalId: String,
        val type: String,
        val key: String,
        var value: String,
        val prefilled: Boolean,
    )

    fun updateResources(resources: List<TemplateResource>) {
        rows.clear()
        for (resource in resources) {
            val keys = resource.primaryIdentifierKeys ?: continue
            for (key in keys) {
                val prefilledValue = resource.primaryIdentifier?.get(key)
                rows.add(IdentifierRow(resource.logicalId, resource.type, key, prefilledValue ?: "", prefilledValue != null))
            }
        }
        fireTableDataChanged()
    }

    fun getResourcesToImport(): List<ResourceToImport> {
        val grouped = rows.groupBy { it.logicalId }
        return grouped.mapNotNull { (logicalId, identifierRows) ->
            val identifiers = identifierRows.associate { it.key to it.value }
            if (identifiers.values.any { it.isBlank() }) return@mapNotNull null
            val type = identifierRows.first().type
            ResourceToImport(resourceType = type, logicalResourceId = logicalId, resourceIdentifier = identifiers)
        }
    }

    fun getResourcesWithBlankIdentifiers(): List<String> =
        rows.filter { it.value.isBlank() }.map { "${it.logicalId}.${it.key}" }.distinct()

    override fun getRowCount() = rows.size
    override fun getColumnCount() = columns.size
    override fun getColumnName(col: Int) = columns[col]
    override fun isCellEditable(row: Int, col: Int) = col == 3
    override fun getValueAt(row: Int, col: Int): Any = when (col) {
        0 -> rows[row].logicalId
        1 -> rows[row].type
        2 -> rows[row].key
        3 -> rows[row].value
        else -> ""
    }
    override fun setValueAt(value: Any?, row: Int, col: Int) {
        if (col == 3) {
            rows[row].value = value?.toString() ?: ""
            fireTableCellUpdated(row, col)
        }
    }
}
