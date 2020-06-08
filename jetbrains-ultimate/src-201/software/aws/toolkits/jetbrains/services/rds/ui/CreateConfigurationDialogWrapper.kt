package software.aws.toolkits.jetbrains.services.rds.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import software.aws.toolkits.jetbrains.services.iam.IamRole
import software.aws.toolkits.resources.message
import javax.swing.JComponent

class CreateConfigurationDialogWrapper(private val project: Project) : DialogWrapper(project) {
    private val panel = CreateConfigurationDialog(project)
    init {
        //isOKActionEnabled = false
        title = "TODO configuration"
        setOKButtonText(message("general.create_button"))

        init()
    }

    override fun createCenterPanel(): JComponent? = panel.panel
    fun getRole():IamRole? = panel.resourceSelector.selected()
    fun getDatabase(): String = panel.databaseName.text
}
