// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package migration.software.aws.toolkits.jetbrains.services.codewhisperer.explorer

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import software.amazon.q.jetbrains.core.credentials.AwsBearerTokenConnection
import software.amazon.q.jetbrains.core.credentials.ToolkitConnectionManager
import software.amazon.q.jetbrains.core.credentials.pinning.QConnection
import software.amazon.q.jetbrains.core.credentials.sono.isSono
import software.amazon.q.jetbrains.core.credentials.sso.bearer.BearerTokenAuthState
import software.amazon.q.jetbrains.core.credentials.sso.bearer.BearerTokenProvider
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererLoginType
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExploreActionState
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExploreStateType
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getConnectionStartUrl
import software.aws.toolkits.telemetry.AwsTelemetry

// TODO: refactor this class, now it's managing action and state
@State(name = "codewhispererStates", storages = [Storage("amazonq.xml")])
class CodeWhispererExplorerActionManager : PersistentStateComponent<CodeWhispererExploreActionState> {
    private val actionState = CodeWhispererExploreActionState()
    private val suspendedConnections = mutableSetOf<String>()

    fun setSuspended(project: Project) {
        val startUrl = getCodeWhispererConnectionStartUrl(project)
        if (!suspendedConnections.add(startUrl)) {
            return
        }
    }

    private fun getCodeWhispererConnectionStartUrl(project: Project): String {
        val connection = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance())
        return getConnectionStartUrl(connection) ?: CodeWhispererConstants.ACCOUNTLESS_START_URL
    }

    fun isAutoEnabled(): Boolean = actionState.value.getOrDefault(CodeWhispererExploreStateType.IsAutoEnabled, true)

    fun setAutoEnabled(isAutoEnabled: Boolean) {
        actionState.value[CodeWhispererExploreStateType.IsAutoEnabled] = isAutoEnabled
    }

    fun isAutoEnabledForCodeScan(): Boolean = actionState.value.getOrDefault(CodeWhispererExploreStateType.IsAutoCodeScanEnabled, false)

    fun setAutoEnabledForCodeScan(isAutoEnabledForCodeScan: Boolean) {
        actionState.value[CodeWhispererExploreStateType.IsAutoCodeScanEnabled] = isAutoEnabledForCodeScan
    }

    fun isMonthlyQuotaForCodeScansExceeded(): Boolean = actionState.value.getOrDefault(CodeWhispererExploreStateType.IsMonthlyQuotaForCodeScansExceeded, false)

    fun setMonthlyQuotaForCodeScansExceeded(isMonthlyQuotaForCodeScansExceeded: Boolean) {
        actionState.value[CodeWhispererExploreStateType.IsMonthlyQuotaForCodeScansExceeded] = isMonthlyQuotaForCodeScansExceeded
    }

    fun setHasShownNewOnboardingPage(hasShownNewOnboardingPage: Boolean) {
        actionState.value[CodeWhispererExploreStateType.HasShownNewOnboardingPage] = hasShownNewOnboardingPage
    }

    fun getConnectionExpiredDoNotShowAgain(): Boolean = actionState.value.getOrDefault(CodeWhispererExploreStateType.ConnectionExpiredDoNotShowAgain, false)

    fun setConnectionExpiredDoNotShowAgain(doNotShowAgain: Boolean) {
        actionState.value[CodeWhispererExploreStateType.ConnectionExpiredDoNotShowAgain] = doNotShowAgain
    }

    fun getSessionConfigurationMessageShown(): Boolean = actionState.value.getOrDefault(CodeWhispererExploreStateType.SessionConfigurationMessageShown, false)

    fun setSessionConfigurationMessageShown(isShown: Boolean) {
        actionState.value[CodeWhispererExploreStateType.SessionConfigurationMessageShown] = isShown
    }

    fun setAutoSuggestion(project: Project, isAutoEnabled: Boolean) {
        setAutoEnabled(isAutoEnabled)
        val autoSuggestionState = if (isAutoEnabled) CodeWhispererConstants.AutoSuggestion.ACTIVATED else CodeWhispererConstants.AutoSuggestion.DEACTIVATED
        AwsTelemetry.modifySetting(project, settingId = CodeWhispererConstants.AutoSuggestion.SETTING_ID, settingState = autoSuggestionState)
    }

    // Adding Auto CodeScan Function
    fun setAutoCodeScan(project: Project, isAutoEnabledForCodeScan: Boolean) {
        setAutoEnabledForCodeScan(isAutoEnabledForCodeScan)
        val autoCodeScanState = if (isAutoEnabledForCodeScan) CodeWhispererConstants.AutoCodeScan.ACTIVATED else CodeWhispererConstants.AutoCodeScan.DEACTIVATED
        AwsTelemetry.modifySetting(project, settingId = CodeWhispererConstants.AutoCodeScan.SETTING_ID, settingState = autoCodeScanState)
    }

    fun getIsFirstRestartAfterQInstall(): Boolean = actionState.value.getOrDefault(CodeWhispererExploreStateType.IsFirstRestartAfterQInstall, true)

    fun setIsFirstRestartAfterQInstall(isFirstRestartAfterQInstall: Boolean) {
        actionState.value[CodeWhispererExploreStateType.IsFirstRestartAfterQInstall] = isFirstRestartAfterQInstall
    }

    fun checkActiveCodeWhispererConnectionType(project: Project): CodeWhispererLoginType {
        val conn = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance()) as? AwsBearerTokenConnection
        return conn?.let {
            val provider = (it.getConnectionSettings().tokenProvider.delegate as? BearerTokenProvider) ?: return@let CodeWhispererLoginType.Logout

            when (provider.state()) {
                BearerTokenAuthState.AUTHORIZED -> {
                    if (it.isSono()) {
                        CodeWhispererLoginType.Sono
                    } else {
                        CodeWhispererLoginType.SSO
                    }
                }

                BearerTokenAuthState.NEEDS_REFRESH -> CodeWhispererLoginType.Expired

                BearerTokenAuthState.NOT_AUTHENTICATED -> CodeWhispererLoginType.Logout
            }
        } ?: CodeWhispererLoginType.Logout
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
    }
}
