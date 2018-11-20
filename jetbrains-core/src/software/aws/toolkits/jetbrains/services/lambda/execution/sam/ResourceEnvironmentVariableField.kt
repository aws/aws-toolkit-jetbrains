// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.execution.sam

import com.intellij.execution.util.EnvVariablesTable
import com.intellij.execution.util.EnvironmentVariable
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ComboBoxCellEditor
import software.aws.toolkits.jetbrains.services.cloudformation.Resource
import software.aws.toolkits.jetbrains.services.cloudformation.ResourceMapping
import software.aws.toolkits.jetbrains.services.cloudformation.Variable
import software.aws.toolkits.jetbrains.ui.EnvironmentVariablesTextField
import software.aws.toolkits.jetbrains.ui.ExtensibleEnvVariablesTable
import java.util.*
import javax.swing.table.TableCellEditor

class ResourceEnvironmentVariablesField : EnvironmentVariablesTextField() {
    private var resourceMapping: ResourceMapping = ResourceMapping.EMPTY
    private var variablesToBind: Map<String, Variable> = Collections.emptyMap()

    fun setResourceMapping(mapping: ResourceMapping) {
        resourceMapping = mapping
        updateAutomaticValues()
    }

    fun setFunctionToBind(function: Resource?) {
        setVariablesToBind(function?.getEnvironmentVariables() ?: emptySequence())
    }

    fun setVariablesToBind(models: Sequence<Variable>) {
        variablesToBind = models.filter { it.isReference() }
                .associate { it.variableName to it }

        protectVariables(variablesToBind.keys)
        updateAutomaticValues()
    }

    private fun updateAutomaticValues() {
        val guesses = computeGuesses()

        if (guesses.isNotEmpty()) {
            val newData = mutableMapOf<String, String>() + envVars + guesses
            envVars = newData
        }
    }

    private fun computeGuesses(): Map<String, String> {
        if (variablesToBind.isEmpty() || resourceMapping.isEmpty()) {
            return Collections.emptyMap()
        }

        val guessedValues = mutableMapOf<String, String>()
        val allNeedsValue = variablesToBind.filter { (name, _) -> needsValue(name) }
        allNeedsValue.forEach { (name, variable) ->
            val nextPhysical = resourceMapping.guessPhysicalResourceId(variable.variableValue)
            nextPhysical?.apply { guessedValues[name] = nextPhysical }
        }
        return guessedValues
    }

    private fun needsValue(varName: String): Boolean = envVars.getOrDefault(varName, "").isBlank()

    override fun createProtectedVariable(name: String, value: String): EnvironmentVariable {
        val model = variablesToBind[name]
        return model?.let { ResourceReferenceVariable(name, value, it) }
                ?: throw IllegalArgumentException("Unexpected variable name: $name")
    }

    override fun createDialogTable(): EnvVariablesTable {
        return HintedEnvVariablesTable()
    }

    private inner class HintedEnvVariablesTable : ExtensibleEnvVariablesTable() {
        override fun createValueColumn(): ColumnInfo<EnvironmentVariable, String> {
            return HintedValueColumn()
        }

        private inner class HintedValueColumn : ValueColumn() {

            override fun isCellEditable(environmentVariable: EnvironmentVariable): Boolean {
                return true
            }

            override fun getEditor(variable: EnvironmentVariable): TableCellEditor {
                return when (variable) {
                    is ResourceReferenceVariable -> getComboBoxEditor(variable.model)
                    else -> super.getEditor(variable)
                }
            }

            private fun getComboBoxEditor(model: Variable): TableCellEditor {
                val logicalResource = model.variableValue
                val result = object : ComboBoxCellEditor() {
                    override fun getComboBoxItems(): List<String> {
                        return resourceMapping.listAllSummaries(logicalResource)
                                .map { it.physicalResourceId() }
                                .toList()
                    }

                    override fun isComboboxEditable() = true
                }
                result.clickCountToStart = 1
                return result
            }
        }
    }

    private class ResourceReferenceVariable(name: String, value: String, internal val model: Variable) :
            EnvironmentVariable(name, value, true) {

        override fun getNameIsWriteable(): Boolean {
            return false
        }
    }

}
