// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ui.resources

import com.intellij.util.TextFieldCompletionProviderDumbAware

abstract class PrefixBasedCompletionProvider(caseInsensitivity: Boolean) : TextFieldCompletionProviderDumbAware(caseInsensitivity)
