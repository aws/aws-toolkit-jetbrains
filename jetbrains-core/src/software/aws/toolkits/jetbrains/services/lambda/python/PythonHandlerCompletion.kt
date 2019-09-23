// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.python

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.services.lambda.completion.HandlerCompletion

class PythonHandlerCompletion : HandlerCompletion {

    override fun isSupported(): Boolean = false

    override fun getLookupElements(project: Project): Collection<LookupElement> = emptyList()

    override fun getPrefixMatcher(prefix: String): PrefixMatcher = PrefixMatcher.ALWAYS_TRUE
}
