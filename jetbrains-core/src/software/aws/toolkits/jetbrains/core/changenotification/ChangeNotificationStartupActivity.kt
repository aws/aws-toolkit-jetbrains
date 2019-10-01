package software.aws.toolkits.jetbrains.core.changenotification

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class ChangeNotificationStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        val changeNotificationManager =
            ServiceManager.getService(ChangeNotificationManager::class.java)

        changeNotificationManager.checkAndNotify(project)
    }
}
