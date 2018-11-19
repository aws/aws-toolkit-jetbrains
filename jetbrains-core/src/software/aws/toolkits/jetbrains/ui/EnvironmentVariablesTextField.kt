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
import java.util.*
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

    fun protectVariables(varNames: List<String>) {
        protectedVarNames = varNames
        text = stringify()
    }

    private fun isProtectedVar(varName: String) = protectedVarNames.contains(varName)

    private fun protectedEntries(): List<Pair<String, String>> =
            protectedVarNames.map { name -> name to envVars.getOrDefault(name, "") }

    private fun unprotectedEntries(): List<Pair<String, String>> =
            envVars.filter { (name, _) -> !isProtectedVar(name) }
                    .map { (name, value) -> name to value }

    fun convertToVariables(): List<EnvironmentVariable> =
            protectedEntries().map { (name, value) -> VariableWithProtection(name, value, true) } +
                    unprotectedEntries().map { (name, value) -> VariableWithProtection(name, value, false) }

    private fun stringify(): String {
        val buf = StringBuilder()

        val entries = protectedEntries().filter { (_, value) -> value.isNotBlank() } + unprotectedEntries()
        return entries.joinTo(buf, ";") { (key, value) -> "$key=$value" }
                .toString()
    }

    private fun acceptEditedVariables(editedVariables: List<EnvironmentVariable>) {
        val newEnvVars = LinkedHashMap<String, String>()
        editedVariables
                .filter { it -> !isProtectedVar(it.name) || it.value.isNotBlank() }
                .forEach { it -> newEnvVars[it.name] = it.value.trim() }

        envVars = newEnvVars
    }

    @TestOnly
    fun acceptEditedVariablesForTesting(testVariables: List<EnvironmentVariable>) =
            acceptEditedVariables(testVariables)

    protected fun createDialogTable(): DialogTable = DelegatingDialogTable()

    private inner class EnvironmentVariablesDialog(parent: Component) : DialogWrapper(parent, true) {
        private val envVarTable = createDialogTable().apply {
            variables = convertToVariables()
        }

        init {
            title = message("environment.variables.dialog.title")
            init()
        }

        override fun createCenterPanel(): JComponent = envVarTable.component

        override fun doOKAction() {
            envVarTable.stopEditing()
            acceptEditedVariables(envVarTable.variables)
            super.doOKAction()
        }
    }

    class VariableWithProtection(name: String, value: String, private val nameProtected: Boolean = false)
        : EnvironmentVariable(name, value, nameProtected) {

        var customDescription: String? = null

        override fun getDescription(): String? {
            return customDescription
        }

        override fun getNameIsWriteable(): Boolean {
            return !nameProtected
        }
    }

    protected interface DialogTable {
        var variables: List<EnvironmentVariable>
        val component: JComponent

        fun stopEditing()
    }

    protected class DelegatingDialogTable(private val delegate: EnvVariablesTable = EnvVariablesTable()) : DialogTable {
        override var variables: List<EnvironmentVariable>
            get() = delegate.environmentVariables
            set(value) {
                delegate.setValues(value)
            }

        override fun stopEditing() {
            delegate.stopEditing()
        }

        override val component: JComponent = delegate.component
    }

}
