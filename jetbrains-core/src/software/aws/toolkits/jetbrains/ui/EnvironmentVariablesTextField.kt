// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ui

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.util.EnvVariablesTable
import com.intellij.execution.util.EnvironmentVariable
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import org.jetbrains.annotations.TestOnly
import software.aws.toolkits.resources.message
import java.awt.Component
import java.util.LinkedHashMap
import javax.swing.JComponent

/**
 * Our version of [com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton] to fit our
 * needs but with similar UX so users are used to it. Namely we
 * <ul>
 * <li>do not support inheriting system env vars</li>
 * <li>have an optional notion of "protected" variables, which have read-only names, can't be removed and should be shown above the others in the dialog</li>
 * </ul>
 * When there are no protection, UX is the same
 */
open class EnvironmentVariablesTextField : TextFieldWithBrowseButton() {
    private var protectedVarNames = emptyList<String>()

    private var data = EnvironmentVariablesData.create(emptyMap(), false)
    var envVars: Map<String, String>
        get() = data.envs
        set(value) {
            data = EnvironmentVariablesData.create(value, false)
            text = stringify()
        }

    init {
        isEditable = false
        addActionListener {
            EnvironmentVariablesDialog(this).show()
        }
    }

    fun protectVariables(varNames: Collection<String>) {
        protectedVarNames = varNames.toList()
        text = stringify()
    }

    private fun isProtectedVar(varName: String) = protectedVarNames.contains(varName)

    private fun protectedEntries(): List<Pair<String, String>> =
            protectedVarNames.map { name -> name to envVars.getOrDefault(name, "") }

    private fun unprotectedEntries(): List<Pair<String, String>> =
            envVars.filter { (name, _) -> !isProtectedVar(name) }
                    .map { (name, value) -> name to value }

    fun convertToVariables(): List<EnvironmentVariable> =
            protectedEntries().map { (name, value) -> createProtectedVariable(name, value) } +
                    unprotectedEntries().map { (name, value) -> EnvironmentVariable(name, value, false) }

    protected open fun createProtectedVariable(name: String, value: String): EnvironmentVariable =
            object : EnvironmentVariable(name, value, true) {
                override fun getNameIsWriteable(): Boolean = false
            }

    private fun stringify(): String {
        val entries = protectedEntries().filter { (_, value) -> value.isNotBlank() } + unprotectedEntries()
        return entries.joinTo(StringBuilder(), ";") { (key, value) -> "$key=$value" }
                .toString()
    }

    private fun acceptEditedVariables(editedVariables: List<EnvironmentVariable>) {
        val newEnvVars = LinkedHashMap<String, String>()
        editedVariables
                .filter { it -> !isProtectedVar(it.name) || it.value?.isNotBlank() ?: false }
                .forEach { it -> newEnvVars[it.name] = it.value.trim() }

        envVars = newEnvVars
    }

    @TestOnly
    fun acceptEditedVariablesForTesting(testVariables: List<EnvironmentVariable>) =
            acceptEditedVariables(testVariables)

    protected open fun createDialogTable(): EnvVariablesTable = EnvVariablesTable()

    private inner class EnvironmentVariablesDialog(parent: Component) : DialogWrapper(parent, true) {
        private val envVarTable = createDialogTable().apply {
            setValues(convertToVariables())
        }

        init {
            title = message("environment.variables.dialog.title")
            init()
        }

        override fun createCenterPanel(): JComponent = envVarTable.component

        override fun doOKAction() {
            envVarTable.stopEditing()
            acceptEditedVariables(envVarTable.environmentVariables)
            super.doOKAction()
        }
    }
}
