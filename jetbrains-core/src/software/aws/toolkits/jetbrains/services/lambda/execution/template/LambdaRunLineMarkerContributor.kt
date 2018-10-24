// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.execution.template

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import org.jetbrains.yaml.psi.YAMLKeyValue
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.functionFromElement

class LambdaRunLineMarkerContributor : RunLineMarkerContributor() {

    override fun getInfo(element: PsiElement): Info? {
        // Only leaf element is allowed
        if (element.firstChild != null) {
            return null
        }

        val parent = element.parent ?: return null
        return try {
            functionFromElement(parent) ?: return null

            when (parent) {
                is YAMLKeyValue -> {
                    // Only mark the key element
                    if (parent.key != element) {
                        return null
                    }
                }
                else -> return null
            }

            Info(AllIcons.RunConfigurations.TestState.Run,
                    ExecutorAction.getActions(1), null)
        } catch (e: Exception) {
            null
        }
    }
}