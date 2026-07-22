// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.utils.extensions

import com.intellij.testFramework.TestApplicationManager
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Sets up the IntelliJ test [com.intellij.openapi.application.Application] for JUnit5 tests.
 *
 * This replaces the platform's `com.intellij.testFramework.ApplicationExtension`, which was removed in 2026.2.
 * Its modern replacements (`@TestApplication` / `TestApplicationExtension`) additionally assert on teardown that no
 * non-default projects leaked; a number of Toolkit tests do leak, so this preserves the pre-2026.2 behavior of
 * bringing up the test application without the leak assertion.
 */
class ApplicationExtension : BeforeAllCallback {
    override fun beforeAll(context: ExtensionContext) {
        TestApplicationManager.getInstance()
    }
}
