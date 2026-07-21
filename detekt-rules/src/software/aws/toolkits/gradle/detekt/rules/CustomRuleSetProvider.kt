// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.gradle.detekt.rules

import dev.detekt.api.Config
import dev.detekt.api.RuleName
import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

class CustomRuleSetProvider : RuleSetProvider {
    override val ruleSetId: RuleSetId = RuleSetId("CustomDetektRules")
    override fun instance(): RuleSet = RuleSet(
        ruleSetId,
        mapOf(
            RuleName("BannedPattern") to
                { config: Config -> BannedPatternRule(config, BannedPatternRule.DEFAULT_PATTERNS) },
            RuleName("LazyLog") to ::LazyLogRule,
            RuleName("RunInEdtWithoutModalityInDialog") to ::DialogModalityRule,
            RuleName("BannedImports") to ::BannedImportsRule,
        ),
    )
}
