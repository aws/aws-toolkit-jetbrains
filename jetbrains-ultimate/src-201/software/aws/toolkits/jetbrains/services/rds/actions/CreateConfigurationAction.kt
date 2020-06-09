package software.aws.toolkits.jetbrains.services.rds.actions

import com.intellij.database.dataSource.DatabaseDriverManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import software.aws.toolkits.jetbrains.core.credentials.activeCredentialProvider
import software.aws.toolkits.jetbrains.core.credentials.activeRegion
import software.aws.toolkits.jetbrains.core.explorer.actions.SingleExplorerNodeActionGroup
import software.aws.toolkits.jetbrains.core.help.HelpIds
import software.aws.toolkits.jetbrains.services.rds.RdsNode
import software.aws.toolkits.jetbrains.services.rds.auth.IamAuth
import software.aws.toolkits.jetbrains.services.rds.auth.IamAuth.Companion.CREDENTIAL_ID_PROPERTY
import software.aws.toolkits.jetbrains.services.rds.auth.IamAuth.Companion.REGION_ID_PROPERTY
import software.aws.toolkits.jetbrains.services.rds.jdbcMysql
import software.aws.toolkits.jetbrains.services.rds.jdbcPostgres
import software.aws.toolkits.jetbrains.services.rds.mysqlEngineType
import software.aws.toolkits.jetbrains.services.rds.postgresEngineType
import software.aws.toolkits.jetbrains.services.rds.ui.CreateConfigurationDialogWrapper
import software.aws.toolkits.jetbrains.utils.actions.OpenBrowserAction
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.resources.message

// It is registered in ext-datagrip.xml FIX_WHEN_MIN_IS_201
@Suppress("ComponentNotRegistered")
class CreateConfigurationActionGroup : SingleExplorerNodeActionGroup<RdsNode>("TODO add config"), DumbAware {
    override fun getChildren(selected: RdsNode, e: AnActionEvent): List<AnAction> = listOf(
        CreateIamConfigurationAction(selected)
    )
}

class CreateIamConfigurationAction(private val node: RdsNode) : AnAction(message("rds.iam_config")) {
    override fun actionPerformed(e: AnActionEvent) {
        if (!checkPrerequisites()) {
            return
        }
        val dialog = CreateConfigurationDialogWrapper(node.nodeProject, node.dbInstance)
        if (!dialog.showAndGet()) {
            return
        }
        val username = dialog.getUsername() ?: throw IllegalStateException("Username is null, but it should have already been validated not null!")
        val database = dialog.getDatabaseName()
        createDatasource(username, database)
    }

    private fun checkPrerequisites(): Boolean {
        // Assert IAM auth enabled
        if (!node.dbInstance.iamDatabaseAuthenticationEnabled()) {
            notifyError(
                project = node.nodeProject,
                title = message("aws.notification.title"),
                content = message("validation_error.no_iam_auth", node.dbInstance.dbName()),
                action = OpenBrowserAction(message("rds.validation_error.guide"), null, HelpIds.RDS_SETUP_IAM_AUTH.url)
            )
            return false
        }
        return true
    }

    private fun createDatasource(username: String, database: String) {
        val endpoint = node.dbInstance.endpoint()
        val url = "${endpoint.address()}:${endpoint.port()}"
        val source = LocalDataSourceManager.getInstance(node.nodeProject)
        val dataSource = LocalDataSource().also {
            // turn on url only config since we don't need any additional config
            it.isConfiguredByUrl = true
            it.authProviderId = IamAuth.providerId
            // TODO add boxes
            it.additionalJdbcProperties[CREDENTIAL_ID_PROPERTY] = node.nodeProject.activeCredentialProvider().id
            it.additionalJdbcProperties[REGION_ID_PROPERTY] = node.nodeProject.activeRegion().id
        }

        when (node.dbInstance.engine()) {
            mysqlEngineType -> {
                dataSource.username = username
                // For mysql there are mysql and mysql.8 (which renders as mysql). mysql is for 5.1, but the minimum
                // version for RDS is 5.5 so always set mysql.8
                dataSource.databaseDriver = DatabaseDriverManager.getInstance().getDriver("mysql.8")
                dataSource.url = "jdbc:$jdbcMysql://$url/$database"
            }
            postgresEngineType -> {
                // In postgres this is case sensitive as lower case
                dataSource.username = username.toLowerCase()
                dataSource.databaseDriver = DatabaseDriverManager.getInstance().getDriver("postgresql")
                dataSource.url = "jdbc:$jdbcPostgres://$url/$database"
            }
            else -> throw IllegalArgumentException("Engine ${node.dbInstance.engine()} is not supported!")
        }
        dataSource.name = message("rds.iam_config_name", dataSource.username, url)

        source.addDataSource(dataSource)
    }
}
