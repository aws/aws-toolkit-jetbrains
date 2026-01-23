// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package compat.com.intellij.lang.javascript

// inline to avoid loading this through core classpath
inline val JavascriptLanguage
    get() = com.intellij.lang.javascript.JavascriptLanguage.INSTANCE
