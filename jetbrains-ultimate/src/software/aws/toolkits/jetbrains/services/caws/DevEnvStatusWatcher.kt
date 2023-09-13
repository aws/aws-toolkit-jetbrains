// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.caws

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.ui.MessageDialogBuilder
import com.jetbrains.rdserver.unattendedHost.UnattendedStatusUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.codecatalyst.CodeCatalystClient
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.coroutines.getCoroutineBgContext
import software.aws.toolkits.jetbrains.core.coroutines.getCoroutineUiContext
import software.aws.toolkits.jetbrains.core.coroutines.projectCoroutineScope
import software.aws.toolkits.jetbrains.core.credentials.sono.SonoCredentialManager
import software.aws.toolkits.jetbrains.services.caws.envclient.CawsEnvironmentClient
import software.aws.toolkits.jetbrains.services.caws.envclient.models.UpdateActivityRequest
import software.aws.toolkits.jetbrains.utils.isRunningOnRemoteBackend
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.resources.message
import java.time.Instant

class DevEnvStatusWatcher : StartupActivity {

    companion object {
        private val LOG = getLogger<DevEnvStatusWatcher>()
    }

    override fun runActivity(project: Project) {
        if (!isRunningOnRemoteBackend() && System.getenv(CawsConstants.CAWS_ENV_ID_VAR) == null) {
            return
        }
        val connection = SonoCredentialManager.getInstance(project).getConnectionSettings()
            ?: error("Failed to fetch connection settings from Dev Environment")
        val envId = System.getenv(CawsConstants.CAWS_ENV_ID_VAR) ?: error("envId env var null")
        val org = System.getenv(CawsConstants.CAWS_ENV_ORG_NAME_VAR) ?: error("space env var null")
        val projectName = System.getenv(CawsConstants.CAWS_ENV_PROJECT_NAME_VAR) ?: error("project env var null")
        val client = connection.awsClient<CodeCatalystClient>()
        val coroutineScope = projectCoroutineScope(project)
        coroutineScope.launch(getCoroutineBgContext()) {
            val initialEnv = client.getDevEnvironment {
                it.id(envId)
                it.spaceName(org)
                it.projectName(projectName)
            }
            val inactivityTimeout = initialEnv.inactivityTimeoutMinutes()
            if (inactivityTimeout == 0) {
                LOG.info("Dev environment inactivity timeout is 0, not monitoring")
                return@launch
            }
            val inactivityTimeoutInSeconds = inactivityTimeout * 60

            // ensure the JetBrains inactivity tracker and the activity api are in sync
            val jbActivityStatusJson = UnattendedStatusUtil.getStatus()
            val jbActivityStatus = jbActivityStatusJson.projects?.first()?.secondsSinceLastControllerActivity ?: 0
            notifyBackendOfActivity((System.currentTimeMillis() - (jbActivityStatus * 1000)).toString())
            var secondsSinceLastControllerActivity = jbActivityStatus

            while (true) {
                val statusJson = UnattendedStatusUtil.getStatus()
                val lastActivityTime = statusJson.projects?.first()?.secondsSinceLastControllerActivity ?: 0
                if (lastActivityTime < secondsSinceLastControllerActivity) {
                    // update the API in case of any activity
                    notifyBackendOfActivity((System.currentTimeMillis() - (lastActivityTime * 1000)).toString())
                }
                secondsSinceLastControllerActivity = lastActivityTime

                val lastRecordedActivityTime = CawsEnvironmentClient.getInstance().getActivity().timestamp ?: System.currentTimeMillis().toString()
                val now = Instant.now().toEpochMilli()
                val durationRecordedSinceLastActivity = now - lastRecordedActivityTime.toLong()
                val secondsRecordedSinceLastActivity = durationRecordedSinceLastActivity / 1000

                if (secondsRecordedSinceLastActivity >= (inactivityTimeoutInSeconds - 300)) {
                    try {
                        val inactivityDurationInMinutes = secondsRecordedSinceLastActivity / 60
                        val ans = runBlocking {
                            val continueWorking = withContext(getCoroutineUiContext()) {
                                return@withContext MessageDialogBuilder.okCancel(
                                    message("caws.devenv.continue.working.after.timeout.title"),
                                    message("caws.devenv.continue.working.after.timeout", inactivityDurationInMinutes)
                                ).ask(project)
                            }
                            return@runBlocking continueWorking
                        }

                        if (ans) {
                            notifyBackendOfActivity(System.currentTimeMillis().toString())
                        }
                    } catch (e: Exception) {
                        val preMessage = "Error while checking if Dev Environment should continue working"
                        LOG.error(e) { preMessage }
                        notifyError(preMessage, e.message.toString())
                    }
                }
                delay(30000)
            }
        }
    }

    fun notifyBackendOfActivity(timestamp: String = System.currentTimeMillis().toString()) {
        val request = UpdateActivityRequest(
            timestamp = timestamp
        )
        CawsEnvironmentClient.getInstance().putActivityTimestamp(request)
    }
}
