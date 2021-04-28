// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.gradle.detekt.rules

import com.pinterest.ktlint.core.RuleSet
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class CustomRuleSetProvider : RuleSetProvider {
    override fun get() = RuleSet(
        "custom-detekt-rules",
        listOf(
        CopyrightHeaderRule(),
        BannedPatternRule(BannedPatternRule.DEFAULT_PATTERNS),
        ExpressionBodyRule(),
        LazyLogRule(),
        DialogModalityRule(),
        BannedImportsRule()
        )
    )
}
