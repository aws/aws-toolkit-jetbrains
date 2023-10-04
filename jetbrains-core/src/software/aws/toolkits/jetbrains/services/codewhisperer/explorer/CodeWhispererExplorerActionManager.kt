// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.explorer

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Property
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.CodeWhispererConnection
import software.aws.toolkits.jetbrains.core.credentials.sono.isSono
import software.aws.toolkits.jetbrains.core.explorer.refreshDevToolTree
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererLoginType
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getConnectionStartUrl
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.isRefreshTokenExpired
import software.aws.toolkits.telemetry.AwsTelemetry
import java.time.LocalDateTime

// TODO: refactor this class, now it's managing action and state
@State(name = "codewhispererStates", storages = [Storage("aws.xml")])
class CodeWhispererExplorerActionManager : PersistentStateComponent<CodeWhispererExploreActionState> {
    private val actionState = CodeWhispererExploreActionState()
    private val suspendedConnections = mutableSetOf<String>()

    fun isSuspended(project: Project): Boolean {
        val startUrl = getCodeWhispererConnectionStartUrl(project)
        return suspendedConnections.contains(startUrl)
    }

    fun setSuspended(project: Project) {
        val startUrl = getCodeWhispererConnectionStartUrl(project)
        if (!suspendedConnections.add(startUrl)) {
            return
        }
        project.refreshDevToolTree()
    }

    private fun getCodeWhispererConnectionStartUrl(project: Project): String {
        val connection = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(CodeWhispererConnection.getInstance())
        return getConnectionStartUrl(connection) ?: CodeWhispererConstants.ACCOUNTLESS_START_URL
    }

    fun isAutoEnabled(): Boolean = actionState.value.getOrDefault(CodeWhispererExploreStateType.IsAutoEnabled, true)

    fun setAutoEnabled(isAutoEnabled: Boolean) {
        actionState.value[CodeWhispererExploreStateType.IsAutoEnabled] = isAutoEnabled
    }

    fun hasShownOldOnboardingPage(): Boolean = actionState.value.getOrDefault(CodeWhispererExploreStateType.HasShownHowToUseCodeWhisperer, false)

    fun hasShownNewOnboardingPage(): Boolean = actionState.value.getOrDefault(CodeWhispererExploreStateType.HasShownNewOnboardingPage, false)

    fun setHasShownNewOnboardingPage(hasShownNewOnboardingPage: Boolean) {
        actionState.value[CodeWhispererExploreStateType.HasShownNewOnboardingPage] = hasShownNewOnboardingPage
    }

    fun setAccountlessNotificationWarnTimestamp() {
        actionState.accountlessWarnTimestamp = LocalDateTime.now().format(CodeWhispererConstants.TIMESTAMP_FORMATTER)
    }

    fun setAccountlessNotificationErrorTimestamp() {
        actionState.accountlessErrorTimestamp = LocalDateTime.now().format(CodeWhispererConstants.TIMESTAMP_FORMATTER)
    }

    fun getAccountlessWarnNotificationTimestamp(): String? = actionState.accountlessWarnTimestamp

    fun getAccountlessErrorNotificationTimestamp(): String? = actionState.accountlessErrorTimestamp

    fun getDoNotShowAgainWarn(): Boolean = actionState.value.getOrDefault(CodeWhispererExploreStateType.DoNotShowAgainWarn, false)

    fun setDoNotShowAgainWarn(doNotShowAgain: Boolean) {
        actionState.value[CodeWhispererExploreStateType.DoNotShowAgainWarn] = doNotShowAgain
    }

    fun getDoNotShowAgainError(): Boolean = actionState.value.getOrDefault(CodeWhispererExploreStateType.DoNotShowAgainError, false)

    fun setDoNotShowAgainError(doNotShowAgain: Boolean) {
        actionState.value[CodeWhispererExploreStateType.DoNotShowAgainError] = doNotShowAgain
    }

    fun getConnectionExpiredDoNotShowAgain(): Boolean = actionState.value.getOrDefault(CodeWhispererExploreStateType.ConnectionExpiredDoNotShowAgain, false)

    fun setConnectionExpiredDoNotShowAgain(doNotShowAgain: Boolean) {
        actionState.value[CodeWhispererExploreStateType.ConnectionExpiredDoNotShowAgain] = doNotShowAgain
    }

    fun getAccountlessNullified(): Boolean = actionState.value.getOrDefault(CodeWhispererExploreStateType.AccountlessNullified, false)

    fun setAccountlessNullified(accountlessNullified: Boolean) {
        actionState.value[CodeWhispererExploreStateType.AccountlessNullified] = accountlessNullified
    }

    fun setAutoSuggestion(project: Project, isAutoEnabled: Boolean) {
        setAutoEnabled(isAutoEnabled)
        val autoSuggestionState = if (isAutoEnabled) CodeWhispererConstants.AutoSuggestion.ACTIVATED else CodeWhispererConstants.AutoSuggestion.DEACTIVATED
        AwsTelemetry.modifySetting(project, settingId = CodeWhispererConstants.AutoSuggestion.SETTING_ID, settingState = autoSuggestionState)
        project.refreshDevToolTree()
    }

    @Deprecated("Accountless credential will be removed soon")
    @ScheduledForRemoval
    // Will keep it for existing accountless users
    /**
     * Will be called from CodeWhispererService.showRecommendationInPopup()
     * Caller (e.x. CodeWhispererService) should take care if null value returned, popup a notification/hint window or dialog etc.
     */
    fun resolveAccessToken(): String? {
        if (actionState.token == null) {
            LOG.warn { "Logical Error: Try to get access token before token initialization" }
        }
        return actionState.token
    }

    fun checkActiveCodeWhispererConnectionType(project: Project) = when {
        actionState.token != null -> CodeWhispererLoginType.Accountless
        isRefreshTokenExpired(project) -> CodeWhispererLoginType.Expired
        else -> {
            val conn = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(CodeWhispererConnection.getInstance())
            if (conn != null) {
                if (conn.isSono()) {
                    CodeWhispererLoginType.Sono
                } else {
                    CodeWhispererLoginType.SSO
                }
            } else {
                CodeWhispererLoginType.Logout
            }
        }
    }

    fun nullifyAccountlessCredentialIfNeeded() {
        if (actionState.token != null) {
            setAccountlessNullified(true)
            actionState.token = null
        }
    }

    override fun getState(): CodeWhispererExploreActionState = CodeWhispererExploreActionState().apply {
        value.putAll(actionState.value)
        token = actionState.token
        accountlessWarnTimestamp = actionState.accountlessWarnTimestamp
        accountlessErrorTimestamp = actionState.accountlessErrorTimestamp
    }

    override fun loadState(state: CodeWhispererExploreActionState) {
        actionState.value.clear()
        actionState.token = state.token
        actionState.value.putAll(state.value)
        actionState.accountlessWarnTimestamp = state.accountlessWarnTimestamp
        actionState.accountlessErrorTimestamp = state.accountlessErrorTimestamp
    }

    companion object {
        @JvmStatic
        fun getInstance(): CodeWhispererExplorerActionManager = service()

        private val LOG = getLogger<CodeWhispererExplorerActionManager>()
    }
}

class CodeWhispererExploreActionState : BaseState() {
    @get:Property
    val value by map<CodeWhispererExploreStateType, Boolean>()

    // can not remove this as we want to support existing accountless users
    @get:Property
    var token by string()

    @get:Property
    var accountlessWarnTimestamp by string()

    @get:Property
    var accountlessErrorTimestamp by string()
}

// TODO: Don't remove IsManualEnabled
enum class CodeWhispererExploreStateType {
    IsAutoEnabled,
    IsManualEnabled,
    HasAcceptedTermsOfServices,
    HasShownHowToUseCodeWhisperer,
    HasShownNewOnboardingPage,
    DoNotShowAgainWarn,
    DoNotShowAgainError,
    AccountlessNullified,
    ConnectionExpiredDoNotShowAgain
}

interface CodeWhispererActivationChangedListener {
    fun activationChanged(value: Boolean) {}
}

fun isCodeWhispererEnabled(project: Project) = with(CodeWhispererExplorerActionManager.getInstance()) {
    checkActiveCodeWhispererConnectionType(project) != CodeWhispererLoginType.Logout
}

fun isCodeWhispererExpired(project: Project) = with(CodeWhispererExplorerActionManager.getInstance()) {
    checkActiveCodeWhispererConnectionType(project) == CodeWhispererLoginType.Expired
}
