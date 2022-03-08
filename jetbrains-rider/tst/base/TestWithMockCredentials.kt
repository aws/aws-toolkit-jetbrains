// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package base

import org.testng.annotations.AfterClass
import software.aws.toolkits.jetbrains.core.credentials.MockCredentialManagerRule

interface TestWithMockCredentials {
    val credentialManagerRule: MockCredentialManagerRule

    @AfterClass
    fun cleanUp() {
        credentialManagerRule.reset()
    }
}

class TestWithMockCredentialsDelegate : TestWithMockCredentials {
    override val credentialManagerRule by lazy {
        MockCredentialManagerRule()
    }
}
