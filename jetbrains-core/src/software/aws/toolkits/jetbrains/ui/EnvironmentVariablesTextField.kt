// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ui

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.util.EnvVariablesTable
import com.intellij.execution.util.EnvironmentVariable
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.UserActivityProviderComponent
import com.intellij.util.ui.ListTableModel
import software.aws.toolkits.resources.message
import java.awt.Component
import java.util.LinkedHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import javax.swing.event.DocumentEvent

/**
 * Our version of [com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton] to fit our
 * needs but with same UX so users are used to it. Namely we do not support inheriting system env vars, but rest
 * of UX is the same
 */
class EnvironmentVariablesTextField(private val immutableKeys: Boolean) : TextFieldWithBrowseButton(), UserActivityProviderComponent {
    private var data = EnvironmentVariablesData.create(emptyMap(), false)
    private val listeners = CopyOnWriteArrayList<ChangeListener>()

    var envVars: Map<String, String>
        get() = data.envs
        set(value) {
            data = EnvironmentVariablesData.create(value, false)
            text = stringify(data.envs)
        }

    init {
        addActionListener {
            EnvironmentVariablesDialog(immutableKeys, this).show()
        }

        textField.document.addDocumentListener(
            object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) {
                    if (!StringUtil.equals(stringify(data.envs), text)) {
                        val textEnvs = EnvVariablesTable.parseEnvsFromText(text)
                        data = EnvironmentVariablesData.create(textEnvs, data.isPassParentEnvs)
                        fireStateChanged()
                    }
                }
            }
        )
    }

    private fun convertToVariables(envVars: Map<String, String>, readOnly: Boolean): List<EnvironmentVariable> = envVars.map { (key, value) ->
        EnvironmentVariable(key, value, readOnly)
    }

    override fun getDefaultIcon(): Icon = AllIcons.General.InlineVariables

    override fun getHoveredIcon(): Icon = AllIcons.General.InlineVariablesHover

    override fun addChangeListener(changeListener: ChangeListener) {
        listeners.add(changeListener)
    }

    override fun removeChangeListener(changeListener: ChangeListener) {
        listeners.remove(changeListener)
    }

    private fun fireStateChanged() {
        listeners.forEach {
            it.stateChanged(ChangeEvent(this))
        }
    }

    private fun stringify(envVars: Map<String, String>): String {
        if (envVars.isEmpty()) {
            return ""
        }

        return buildString {
            for ((key, value) in envVars) {
                if (isNotEmpty()) {
                    append(";")
                }
                append(StringUtil.escapeChar(key, ';'))
                append("=")
                append(StringUtil.escapeChar(value, ';'))
            }
        }
    }

    private inner class EnvironmentVariablesDialog(template: Boolean, parent: Component) : DialogWrapper(parent, true) {
        private val envVarTable = object : EnvVariablesTable() {
            override fun createListModel(): ListTableModel<*> =
                ListTableModel<Any>(NameColumnInfo(), object : ValueColumnInfo() {
                    // Always make the value editable, but not necessarily the key
                    override fun isCellEditable(environmentVariable: EnvironmentVariable): Boolean = true
                }
                )
        }.apply {
            setValues(convertToVariables(data.envs, template))
            setPasteActionEnabled(!immutableKeys)
        }

        init {
            title = message("environment.variables.dialog.title")
            init()
        }

        override fun createCenterPanel(): JComponent = envVarTable.component.apply {
            if (immutableKeys) {
                ToolbarDecorator.findAddButton(this)?.let { it.isVisible = false }
                ToolbarDecorator.findRemoveButton(this)?.let { it.isVisible = false }
            }
        }

        override fun doOKAction() {
            envVarTable.stopEditing()
            val newEnvVars = LinkedHashMap<String, String>()
            for (variable in envVarTable.environmentVariables) {
                newEnvVars[variable.name] = variable.value
            }
            envVars = newEnvVars
            super.doOKAction()
        }
    }
}
