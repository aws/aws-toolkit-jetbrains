package software.aws.toolkits.jetbrains.ui

import com.intellij.execution.util.EnvVariablesTable
import com.intellij.execution.util.EnvironmentVariable
import com.intellij.execution.util.StringWithNewLinesCellEditor
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import javax.swing.table.TableCellEditor

/**
 * More extensible version of the platform EnvVariablesTable, allowing to customise separate columns.
 * Still extends the platform super-class to avoid copy-paste of the actions related code.
 */
open class ExtensibleEnvVariablesTable : EnvVariablesTable() {

    override fun createListModel(): ListTableModel<EnvironmentVariable> {
        return ListTableModel(createNameColumn(), createValueColumn())
    }

    protected open fun createNameColumn(): ColumnInfo<EnvironmentVariable, String> = NameColumn()

    protected open fun createValueColumn(): ColumnInfo<EnvironmentVariable, String> = ValueColumn()

    protected inner class NameColumn(name: String = "Name") : ElementsColumnInfoBase<EnvironmentVariable>(name) {
        override fun valueOf(environmentVariable: EnvironmentVariable): String? {
            return environmentVariable.name
        }

        override fun isCellEditable(environmentVariable: EnvironmentVariable): Boolean {
            return environmentVariable.nameIsWriteable
        }

        override fun setValue(environmentVariable: EnvironmentVariable, s: String?) {
            if (s == valueOf(environmentVariable)) {
                return
            }
            environmentVariable.name = s
            setModified()
        }

        override fun getDescription(environmentVariable: EnvironmentVariable): String? {
            return environmentVariable.description
        }
    }

    protected inner class ValueColumn(name: String = "Value") : ElementsColumnInfoBase<EnvironmentVariable>(name) {
        override fun valueOf(environmentVariable: EnvironmentVariable): String? {
            return environmentVariable.value
        }

        override fun isCellEditable(environmentVariable: EnvironmentVariable): Boolean {
            return !environmentVariable.isPredefined
        }

        override fun setValue(environmentVariable: EnvironmentVariable, s: String?) {
            if (s == valueOf(environmentVariable)) {
                return
            }
            environmentVariable.value = s
            setModified()
        }

        override fun getDescription(environmentVariable: EnvironmentVariable): String? {
            return environmentVariable.description
        }

        override fun getEditor(variable: EnvironmentVariable): TableCellEditor {
            val editor = StringWithNewLinesCellEditor()
            editor.clickCountToStart = 1
            return editor
        }
    }
}