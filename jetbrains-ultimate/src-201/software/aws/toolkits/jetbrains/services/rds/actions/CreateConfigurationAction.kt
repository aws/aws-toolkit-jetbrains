package software.aws.toolkits.jetbrains.services.rds.actions

import com.intellij.database.autoconfig.DataSourceRegistry
import com.intellij.database.dataSource.DatabaseDriver
import com.intellij.database.dataSource.DatabaseDriverManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.database.psi.DataSourceManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import software.aws.toolkits.jetbrains.core.AwsResourceCache
import software.aws.toolkits.jetbrains.core.credentials.CredentialManager
import software.aws.toolkits.jetbrains.core.credentials.activeCredentialProvider
import software.aws.toolkits.jetbrains.core.credentials.activeRegion
import software.aws.toolkits.jetbrains.core.explorer.actions.SingleExplorerNodeAction
import software.aws.toolkits.jetbrains.services.rds.RdsNode
import software.aws.toolkits.jetbrains.services.rds.auth.IamAuth
import software.aws.toolkits.jetbrains.services.rds.auth.IamAuth.Companion.CREDENTIAL_ID_PROPERTY
import software.aws.toolkits.jetbrains.services.rds.auth.IamAuth.Companion.REGION_ID_PROPERTY
import software.aws.toolkits.jetbrains.services.sts.StsResources
import java.time.Instant

// It is registered in ext-datagrip.xml FIX_WHEN_MIN_IS_201
@Suppress("ComponentNotRegistered")
class CreateConfigurationAction : SingleExplorerNodeAction<RdsNode>("TODO add config"), DumbAware {
    override fun actionPerformed(selected: RdsNode, e: AnActionEvent) {
        val source = LocalDataSourceManager.getInstance(selected.nodeProject)
        val dataSource = LocalDataSource().also {
            it.authProviderId = IamAuth.providerId
            // TODO set driver based on mysql version. there's mysql and mysql.8 (which renders as mysql)
            it.databaseDriver = DatabaseDriverManager.getInstance().getDriver("mysql.8")
            it.additionalJdbcProperties[CREDENTIAL_ID_PROPERTY] = selected.nodeProject.activeCredentialProvider().id
            it.additionalJdbcProperties[REGION_ID_PROPERTY] = selected.nodeProject.activeRegion().id
            // todo change
            it.username = "Admin"
            it.name = "TEMP TODO ${Instant.now()}"
            // url only config
            it.isConfiguredByUrl = true
        }
        source.addDataSource(dataSource)
    }
}
