// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.annotations.Property

@Service
@State(name = "codewhispererSettings", storages = [Storage("aws.xml", roamingType = RoamingType.DISABLED)])
class CodeWhispererSettings : PersistentStateComponent<CodeWhispererConfiguration> {
    private val state = CodeWhispererConfiguration()

    fun toggleIncludeCodeWithReference(value: Boolean) {
        state.value[CodeWhispererConfigurationType.IsIncludeCodeWithReference] = value
    }

    fun isIncludeCodeWithReference() = state.value.getOrDefault(
        CodeWhispererConfigurationType.IsIncludeCodeWithReference,
        false
    )

    fun getAutoBuildFeatureConfiguration() = state.projectAutoBuildConfigurationMap

    fun toggleAutoBuildFeature(project: String?, value: Boolean) {
        if (project == null) return

        state.projectAutoBuildConfigurationMap[project] = value
    }

    fun isAutoBuildFeatureEnabled(project: String?) = state.projectAutoBuildConfigurationMap.getOrDefault(project, false)

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
            }
        } else {
            state.value[CodeWhispererConfigurationType.IsProjectContextEnabled] = value
        }
    }

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
    )

    fun setProjectContextIndexThreadCount(value: Int) {
        state.intValue[CodeWhispererIntConfigurationType.ProjectContextIndexThreadCount] = value
    }

    fun getProjectContextIndexMaxSize(): Int = state.intValue.getOrDefault(
        CodeWhispererIntConfigurationType.ProjectContextIndexMaxSize,
        250
    )

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

    companion object {
        fun getInstance(): CodeWhispererSettings = service()
    }

    override fun getState(): CodeWhispererConfiguration = CodeWhispererConfiguration().apply {
        value.putAll(state.value)
        intValue.putAll(state.intValue)
        stringValue.putAll(state.stringValue)
    }

    override fun loadState(state: CodeWhispererConfiguration) {
        this.state.value.clear()
        this.state.intValue.clear()
        this.state.stringValue.clear()
        this.state.value.putAll(state.value)
        this.state.intValue.putAll(state.intValue)
        this.state.stringValue.putAll(state.stringValue)
    }
}

class CodeWhispererConfiguration : BaseState() {
    @get:Property
    val value by map<CodeWhispererConfigurationType, Boolean>()
    val intValue by map<CodeWhispererIntConfigurationType, Int>()
    val projectAutoBuildConfigurationMap by map<String, Boolean>()
    val stringValue by map<CodeWhispererStringConfigurationType, String>()
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
}

enum class CodeWhispererStringConfigurationType {
    IgnoredCodeReviewIssues,
}

enum class CodeWhispererIntConfigurationType {
    ProjectContextIndexThreadCount,
    ProjectContextIndexMaxSize,
}
