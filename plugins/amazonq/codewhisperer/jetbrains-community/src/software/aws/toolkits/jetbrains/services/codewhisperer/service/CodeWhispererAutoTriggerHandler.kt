// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.service

import com.intellij.openapi.editor.Editor
import software.amazon.q.core.utils.debug
import software.amazon.q.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.amazonq.CodeWhispererFeatureConfigService
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileManager
import software.aws.toolkits.jetbrains.services.codewhisperer.model.LatencyContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.TriggerTypeInfo
import software.aws.toolkits.telemetry.CodewhispererTriggerType

interface CodeWhispererAutoTriggerHandler {
    fun performAutomatedTriggerAction(
        editor: Editor,
        automatedTriggerType: CodeWhispererAutomatedTriggerType,
        latencyContext: LatencyContext,
    ) {
        val project = editor.project ?: return
        if (QRegionProfileManager.getInstance().hasValidConnectionButNoActiveProfile(project)) {
            return
        }
        val triggerTypeInfo = TriggerTypeInfo(CodewhispererTriggerType.AutoTrigger, automatedTriggerType)

        LOG.debug { "autotriggering CodeWhisperer with type ${automatedTriggerType.telemetryType}" }
        if (CodeWhispererFeatureConfigService.getInstance().getNewAutoTriggerUX()) {
            CodeWhispererServiceNew.getInstance().showRecommendationsInPopup(editor, triggerTypeInfo, latencyContext)
        } else {
            CodeWhispererService.getInstance().showRecommendationsInPopup(editor, triggerTypeInfo, latencyContext)
        }
    }

    companion object {
        private val LOG = getLogger<CodeWhispererAutoTriggerHandler>()
    }
}
