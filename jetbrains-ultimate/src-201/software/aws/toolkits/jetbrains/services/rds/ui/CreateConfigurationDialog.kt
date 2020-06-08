package software.aws.toolkits.jetbrains.services.rds.ui

import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.services.iam.IamResources
import software.aws.toolkits.jetbrains.services.iam.IamRole
import software.aws.toolkits.jetbrains.ui.ResourceSelector
import javax.swing.JPanel
import javax.swing.JTextField

class CreateConfigurationDialog(private val project: Project) {
    lateinit var panel: JPanel
    lateinit var databaseName: JTextField
    lateinit var resourceSelector: ResourceSelector<IamRole>

    private fun createUIComponents() {
        resourceSelector = ResourceSelector.builder(project)
            .resource { IamResources.LIST_ALL }
            .build()

        resourceSelector.addItemListener {
            println(it)
        }
    }
}
