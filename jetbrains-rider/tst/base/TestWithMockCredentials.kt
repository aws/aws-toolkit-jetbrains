// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package base

import org.testng.annotations.AfterClass
import org.testng.annotations.BeforeClass
import software.aws.toolkits.jetbrains.core.credentials.MockCredentialManagerRule

interface TestWithMockCredentials {
    var credentialManagerRule: MockCredentialManagerRule

    @BeforeClass
    fun initializeRule() {
        credentialManagerRule = MockCredentialManagerRule()
    }

    @AfterClass
    fun cleanUp() {
        credentialManagerRule.reset()
    }
}

class TestWithMockCredentialsDelegate : TestWithMockCredentials {
    override lateinit var credentialManagerRule: MockCredentialManagerRule
}
