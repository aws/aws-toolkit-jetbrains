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
import software.aws.toolkits.jetbrains.services.rds.RdsNode
import software.aws.toolkits.jetbrains.services.rds.auth.IamAuth
import software.aws.toolkits.jetbrains.services.rds.auth.IamAuth.Companion.CREDENTIAL_ID_PROPERTY
import software.aws.toolkits.jetbrains.services.rds.auth.IamAuth.Companion.REGION_ID_PROPERTY
import software.aws.toolkits.jetbrains.services.rds.jdbcMysql
import software.aws.toolkits.jetbrains.services.rds.jdbcPostgres
import software.aws.toolkits.jetbrains.services.rds.mysqlEngineType
import software.aws.toolkits.jetbrains.services.rds.postgresEngineType
import software.aws.toolkits.resources.message

// It is registered in ext-datagrip.xml FIX_WHEN_MIN_IS_201
@Suppress("ComponentNotRegistered")
class CreateConfigurationActionGroup : SingleExplorerNodeActionGroup<RdsNode>("TODO add config"), DumbAware {
    override fun getChildren(selected: RdsNode, e: AnActionEvent): List<AnAction> = listOf(
        CreateIamConfigurationAction(selected)
    )
}

class CreateIamConfigurationAction(private val node: RdsNode) : AnAction(message("rds.iam_config")) {
    private fun getDriverId(): String = when (node.dbInstance.engine()) {
        // For mysql there are mysql and mysql.8 (which renders as mysql). mysql is for 5.1, but the minimum
        // version for RDS is 5.5 so always set mysql.8
        mysqlEngineType -> "mysql.8"
        postgresEngineType -> "postgresql"
        else -> throw IllegalArgumentException("Engine ${node.dbInstance.engine()} is not supported!")
    }

    private fun getJdbcId(): String = when (node.dbInstance.engine()) {
        mysqlEngineType -> jdbcMysql
        postgresEngineType -> jdbcPostgres
        else -> throw IllegalArgumentException("Engine ${node.dbInstance.engine()} is not supported!")
    }

    override fun actionPerformed(e: AnActionEvent) {
        // show page
        // assert iam database auth enabled
        // add roll/iam user selector
        val endpoint = node.dbInstance.endpoint()
        val url = "${endpoint.address()}:${endpoint.port()}"
        val source = LocalDataSourceManager.getInstance(node.nodeProject)
        val dataSource = LocalDataSource().also {
            // turn on url only config since we don't need any additional config
            it.isConfiguredByUrl = true
            it.authProviderId = IamAuth.providerId
            it.databaseDriver = DatabaseDriverManager.getInstance().getDriver(getDriverId())
            it.additionalJdbcProperties[CREDENTIAL_ID_PROPERTY] = node.nodeProject.activeCredentialProvider().id
            it.additionalJdbcProperties[REGION_ID_PROPERTY] = node.nodeProject.activeRegion().id
            // todo accept as argument
            it.username = "Admin"
            it.name = message("rds.iam_config_name", it.username, url)
            it.url = "jdbc:${getJdbcId()}://$url/"
        }
        source.addDataSource(dataSource)
    }
}
