// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.remoteDev.caws

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.jetbrains.core.coroutines.disposableCoroutineScope
import software.aws.toolkits.jetbrains.services.caws.envclient.CawsEnvironmentClient
import software.aws.toolkits.jetbrains.services.caws.envclient.models.GetStatusResponse

@Service
class DevfileWatcher : Disposable {
    private var fileChanged = false
    private val job = disposableCoroutineScope(this).launch {
        while (true) {
            val response = try {
                CawsEnvironmentClient.getInstance().getStatus()
            } catch (e: Exception) {
                LOG.error(e) { "Failed to get devfile change status. Terminating watcher" }
                null
            }

            if (response == null) {
                fileChanged = true
                return@launch
            }

            LOG.info { "$fileChanged, $response; ${(response.status == GetStatusResponse.Status.CHANGED)}" }
            fileChanged = (response.status == GetStatusResponse.Status.CHANGED)

            delay(2000)
        }
    }

    fun hasDevfileChanged(): Boolean = fileChanged

    override fun dispose() {
    }

    companion object {
        fun getInstance() = service<DevfileWatcher>()

        const val DEVFILE_PATTERN = "devfile.yaml"
        private val LOG = getLogger<DevfileWatcher>()
    }
}
