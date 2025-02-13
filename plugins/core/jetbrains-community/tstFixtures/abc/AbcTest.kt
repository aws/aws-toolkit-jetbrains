// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package abc

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationExtension
import com.intellij.testFramework.junit5.TestDisposable
import org.junit.Rule
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import software.aws.toolkits.core.rules.SystemPropertyHelper
import software.aws.toolkits.jetbrains.core.credentials.LegacyManagedBearerSsoConnection
import software.aws.toolkits.jetbrains.core.credentials.ManagedBearerSsoConnection
import software.aws.toolkits.jetbrains.core.credentials.sono.Q_SCOPES
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenProvider
import software.aws.toolkits.jetbrains.utils.extensions.SsoLogin
import software.aws.toolkits.jetbrains.utils.extensions.SsoLoginExtension


@ExtendWith(ApplicationExtension::class, SsoLoginExtension::class)
@SsoLogin("amazonq-test-account")
//@DisabledIfEnvironmentVariable(named = "IS_PROD", matches = "false")
class AbcTest {


    @TestDisposable
    private lateinit var disposable: Disposable


    @Rule
    @JvmField
    val systemPropertyHelper = SystemPropertyHelper()

    private lateinit var connection: ManagedBearerSsoConnection



    @BeforeEach
    fun setUp() {
        // Setup test environment
        System.setProperty("AWS_PROFILE","act_cred")

    }


    @Test
    fun `can set up Connection`() {
        println("This ran")
        connection = LegacyManagedBearerSsoConnection("start_url", "us-east-1", Q_SCOPES)
        //ConnectionPinningManager.getInstance().setPinnedConnection(QConnection.getInstance(), connection)
        (connection.getConnectionSettings().tokenProvider.delegate as BearerTokenProvider).reauthenticate()

        //Disposer.register(disposable, connection)
        // MockClientManager.useRealImplementations(disposableExtension.disposable)

    }



}
