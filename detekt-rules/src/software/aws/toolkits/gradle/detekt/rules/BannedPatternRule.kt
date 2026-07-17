// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("BannedPattern")
package software.aws.toolkits.gradle.detekt.rules

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtFile

class BannedPatternRule(config: Config, private val patterns: List<BannedPattern>) : Rule(config, "Banned calls") {
    override fun visitKtFile(file: KtFile) {
        var offset = 0
        file.text.split("\n").forEachIndexed { _, text ->
            patterns.forEach { pattern ->
                val match = pattern.regex.find(text) ?: return@forEach
                val element = file.findElementAt(offset + match.range.first) ?: return@forEach
                report(
                    Finding(
                        Entity.from(element),
                        message = pattern.message,
                    ),
                )
            }
            // account for delimiter
            offset += text.length + 1
        }
    }

    companion object {
        val DEFAULT_PATTERNS = listOf(
            BannedPattern("Runtime\\.valueOf".toRegex(), "Runtime.valueOf is banned, use Runtime.fromValue instead."),
            BannedPattern(
                """com\.intellij\.openapi\.actionSystem\.DataKeys""".toRegex(),
                "DataKeys is not available in all IDEs, use LangDataKeys instead",
            ),
            BannedPattern(
                """PsiUtil\.getPsiFile""".toRegex(),
                "PsiUtil (java-api.jar) is not available in all IDEs, use PsiManager.getInstance(project).findFile() instead",
            ),
            BannedPattern(
                """com\.intellij\.psi\.util\.PsiUtil$""".toRegex(),
                "PsiUtil (java-api.jar) is not available in all IDEs, use PsiUtilCore or PsiManager instead (platform-api.jar)",
            ),
        )
    }
}

data class BannedPattern(val regex: Regex, val message: String)
