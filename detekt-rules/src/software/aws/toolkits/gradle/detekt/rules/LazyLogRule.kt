// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.gradle.detekt.rules

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis

class LazyLogRule(config: Config) :
    Rule(config, "Use lazy logging syntax (e.g. warning {\"abc\"} ) instead of warning(\"abc\")"),
    RequiresAnalysisApi {

    // UI tests have issues with this TODO see if we want multiple detekt.yml files or disable for certain modules in this rule
    private val optOut = setOf("software.aws.toolkits.jetbrains.uitests")

    override fun visitCallExpression(element: KtCallExpression) {
        super.visitCallExpression(element)
        val callName = element.getCallNameExpression() ?: return
        if (!logMethods.contains(callName.text)) {
            return
        }

        if (optOut.any { name -> element.containingKtFile.packageFqName.asString().startsWith(name) }) {
            return
        }

        val receiverExpression =
            (element.getQualifiedExpressionForSelectorOrThis() as? KtQualifiedExpression)?.receiverExpression ?: return

        analyze(element) {
            // Java types (e.g. org.slf4j.Logger) come back as flexible platform types (Logger!); unwrap to get a symbol
            val exprType = receiverExpression.expressionType
            val type = ((exprType as? KaFlexibleType)?.lowerBound ?: exprType)?.symbol?.classId?.asFqNameString()

            if (type !in loggers) {
                return@analyze
            }

            if (element.lambdaArguments.size != 1) {
                report(
                    Finding(
                        Entity.from(element),
                        message = "Use the lambda version of ${receiverExpression.text}.${callName.text} instead",
                    ),
                )
            }
        }
    }

    companion object {
        private val logMethods = setOf("error", "warn", "info", "debug", "trace")
        val loggers = setOf("org.slf4j.Logger")
    }
}
