// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.completion

import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.services.lambda.LambdaHandlerIndex

class DefaultHandlerCompletion : HandlerCompletion {

    override fun getPrefixMatcher(prefix: String): PrefixMatcher =
        PlainPrefixMatcher(prefix)

    override fun getLookupElements(project: Project): Collection<LookupElement> =
        LambdaHandlerIndex.listHandlers(project).map { LookupElementBuilder.create(it) }
}
