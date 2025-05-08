// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.annotations.Property
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.AmazonQBundle

@Service
@State(name = "codewhispererSettings", storages = [Storage("aws.xml")])
class CodeWhispererSettings : PersistentStateComponent<CodeWhispererConfiguration> {
    private val state = CodeWhispererConfiguration()

    fun toggleIncludeCodeWithReference(value: Boolean) {
        state.value[CodeWhispererConfigurationType.IsIncludeCodeWithReference] = value
    }

    fun isIncludeCodeWithReference() = state.value.getOrDefault(
        CodeWhispererConfigurationType.IsIncludeCodeWithReference,
        false
    )

    fun getAutoBuildSetting() = state.autoBuildSetting

    fun toggleAutoBuildFeature(project: String?, value: Boolean) {
        if (project == null) return

        state.autoBuildSetting[project] = value
    }

    fun isAutoBuildFeatureEnabled(project: String?) = state.autoBuildSetting.getOrDefault(project, false)

    fun toggleImportAdder(value: Boolean) {
        state.value[CodeWhispererConfigurationType.IsImportAdderEnabled] = value
    }

    fun isImportAdderEnabled() = state.value.getOrDefault(
        CodeWhispererConfigurationType.IsImportAdderEnabled,
        true
    )

    fun toggleMetricOptIn(value: Boolean) {
        state.value[CodeWhispererConfigurationType.OptInSendingMetric] = value
    }

    fun isMetricOptIn() = state.value.getOrDefault(
        CodeWhispererConfigurationType.OptInSendingMetric,
        true
    )

    fun toggleProjectContextEnabled(value: Boolean, passive: Boolean = false) {
        if (passive) {
            if (!hasEnabledProjectContextOnce()) {
                toggleEnabledProjectContextOnce(true)
                state.value[CodeWhispererConfigurationType.IsProjectContextEnabled] = value
                // todo: hack to bypass module dependency issue (codewhisperer -> shared), should pass [CodeWhispererShowSettingsAction] instead when it's resolved
                val actions = ActionManager.getInstance().getAction("codewhisperer.settings")?.let { listOf(it) }.orEmpty()

                notifyInfo(
                    AmazonQBundle.message("amazonq.title"),
                    AmazonQBundle.message("amazonq.workspace.settings.open.prompt"),
                    notificationActions = actions
                )
            }
        } else {
            state.value[CodeWhispererConfigurationType.IsProjectContextEnabled] = value
        }
    }

    fun toggleWorkspaceContextEnabled(value: Boolean) {
        state.value[CodeWhispererConfigurationType.IsWorkspaceContextEnabled] = value
    }

    fun isWorkspaceContextEnabled() = state.value.getOrDefault(CodeWhispererConfigurationType.IsWorkspaceContextEnabled, true)
    fun isProjectContextEnabled() = state.value.getOrDefault(CodeWhispererConfigurationType.IsProjectContextEnabled, false)

    private fun hasEnabledProjectContextOnce() = state.value.getOrDefault(CodeWhispererConfigurationType.HasEnabledProjectContextOnce, false)

    private fun toggleEnabledProjectContextOnce(value: Boolean) {
        state.value[CodeWhispererConfigurationType.HasEnabledProjectContextOnce] = value
    }

    fun isProjectContextGpu() = state.value.getOrDefault(CodeWhispererConfigurationType.IsProjectContextGpu, false)

    fun toggleProjectContextGpu(value: Boolean) {
        state.value[CodeWhispererConfigurationType.IsProjectContextGpu] = value
    }

    fun getProjectContextIndexThreadCount(): Int = state.intValue.getOrDefault(
        CodeWhispererIntConfigurationType.ProjectContextIndexThreadCount,
        0
    ).coerceIn(CONTEXT_INDEX_THREADS)

    fun setProjectContextIndexThreadCount(value: Int) {
        state.intValue[CodeWhispererIntConfigurationType.ProjectContextIndexThreadCount] = value
    }

    fun getProjectContextIndexMaxSize(): Int = state.intValue.getOrDefault(
        CodeWhispererIntConfigurationType.ProjectContextIndexMaxSize,
        250
    ).coerceIn(CONTEXT_INDEX_SIZE)

    fun setProjectContextIndexMaxSize(value: Int) {
        state.intValue[CodeWhispererIntConfigurationType.ProjectContextIndexMaxSize] = value
    }

    fun getIgnoredCodeReviewIssues(): String = state.stringValue.getOrDefault(
        CodeWhispererStringConfigurationType.IgnoredCodeReviewIssues,
        ""
    )

    fun setIgnoredCodeReviewIssues(value: String) {
        state.stringValue[CodeWhispererStringConfigurationType.IgnoredCodeReviewIssues] = value
    }

    fun isQPrioritizedForTabAccept(): Boolean = state.value.getOrDefault(
        CodeWhispererConfigurationType.IsQPrioritizedForTabAccept,
        true
    )

    fun setQPrioritizedForTabAccept(value: Boolean) {
        state.value[CodeWhispererConfigurationType.IsQPrioritizedForTabAccept] = value
    }

    fun isTabAcceptPriorityNotificationShownOnce(): Boolean = state.value.getOrDefault(
        CodeWhispererConfigurationType.IsTabAcceptPriorityNotificationShownOnce,
        false
    )

    fun setTabAcceptPriorityNotificationShownOnce(value: Boolean) {
        state.value[CodeWhispererConfigurationType.IsTabAcceptPriorityNotificationShownOnce] = value
    }

    override fun getState(): CodeWhispererConfiguration = CodeWhispererConfiguration().apply {
        value.putAll(state.value)
        intValue.putAll(state.intValue)
        stringValue.putAll(state.stringValue)
        autoBuildSetting.putAll(state.autoBuildSetting)
    }

    override fun loadState(state: CodeWhispererConfiguration) {
        this.state.value.clear()
        this.state.intValue.clear()
        this.state.stringValue.clear()
        this.state.autoBuildSetting.clear()
        this.state.value.putAll(state.value)
        this.state.intValue.putAll(state.intValue)
        this.state.stringValue.putAll(state.stringValue)
        this.state.autoBuildSetting.putAll(state.autoBuildSetting)
    }

    companion object {
        fun getInstance(): CodeWhispererSettings = service()

        val CONTEXT_INDEX_SIZE = IntRange(1, 4096)
        val CONTEXT_INDEX_THREADS = IntRange(0, 50)
    }
}

class CodeWhispererConfiguration : BaseState() {
    @get:Property
    val value by map<CodeWhispererConfigurationType, Boolean>()

    @get:Property
    val intValue by map<CodeWhispererIntConfigurationType, Int>()

    @get:Property
    val stringValue by map<CodeWhispererStringConfigurationType, String>()

    @get:Property
    val autoBuildSetting by map<String, Boolean>()
}

enum class CodeWhispererConfigurationType {
    IsIncludeCodeWithReference,
    OptInSendingMetric,
    IsImportAdderEnabled,
    IsAutoUpdateEnabled,
    IsAutoUpdateNotificationEnabled,
    IsAutoUpdateFeatureNotificationShownOnce,
    IsProjectContextEnabled,
    IsProjectContextGpu,
    HasEnabledProjectContextOnce,
    IsQPrioritizedForTabAccept,
    IsTabAcceptPriorityNotificationShownOnce,
    IsWorkspaceContextEnabled,
}

enum class CodeWhispererStringConfigurationType {
    IgnoredCodeReviewIssues,
}

enum class CodeWhispererIntConfigurationType {
    ProjectContextIndexThreadCount,
    ProjectContextIndexMaxSize,
}
