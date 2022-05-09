// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.gradle.detekt.rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.internal.RequiresTypeResolution
import io.gitlab.arturbosch.detekt.rules.safeAs
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression
import org.jetbrains.kotlin.resolve.calls.util.getExplicitReceiverValue
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall

@RequiresTypeResolution
class UseSdkPaginatorRule : Rule() {
    override val issue = Issue("UseSdkPaginator", Severity.Warning, "AWS SDK provides a paginator which can be used for this call", Debt.FIVE_MINS)

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        expression.getCallNameExpression()?.let {
            val call = it.getResolvedCall(bindingContext) ?: return
            val callMethod = call.call.calleeExpression?.safeAs<KtSimpleNameExpression>()?.getReferencedName() ?: return
            if (callMethod.endsWith(PAGINATOR)) {
                return
            }

            val calleeClass = call.getExplicitReceiverValue()?.type ?: return

            if (calleeClass.memberScope.getFunctionNames().contains(Name.identifier("$callMethod$PAGINATOR"))) {
                report(
                    CodeSmell(
                        issue,
                        Entity.from(expression), "Use the SDK paginator for $calleeClass.$callMethod"
                    )
                )
            }
        }
    }

    companion object {
        private const val PAGINATOR = "Paginator"
    }
}
