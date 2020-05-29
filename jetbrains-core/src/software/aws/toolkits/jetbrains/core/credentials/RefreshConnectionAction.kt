package software.aws.toolkits.jetbrains.core.credentials

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import software.aws.toolkits.jetbrains.core.AwsResourceCache
import software.aws.toolkits.resources.message

class RefreshConnectionAction : AnAction(message("settings.refresh.description"), null, AllIcons.Actions.Refresh), DumbAware {

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        when (ProjectAccountSettingsManager.getInstance(project).connectionState) {
            is ConnectionState.InitializingToolkit, is ConnectionState.ValidatingConnection -> e.presentation.isEnabled = false
            else -> e.presentation.isEnabled = true
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ProjectAccountSettingsManager.getInstance(project).refreshConnectionState()
        AwsResourceCache.getInstance(project).clear()
    }
}
