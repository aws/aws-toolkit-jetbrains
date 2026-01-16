// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import com.intellij.openapi.Disposable
import com.intellij.testFramework.ApplicationExtension
import com.intellij.testFramework.junit5.TestDisposable
import org.junit.Rule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import software.amazon.q.core.rules.SystemPropertyHelper
import software.amazon.q.jetbrains.core.credentials.LegacyManagedBearerSsoConnection
import software.amazon.q.jetbrains.core.credentials.ManagedBearerSsoConnection
import software.amazon.q.jetbrains.core.credentials.pinning.ConnectionPinningManager
import software.amazon.q.jetbrains.core.credentials.pinning.QConnection
import software.amazon.q.jetbrains.core.credentials.sono.Q_SCOPES
import software.amazon.q.jetbrains.core.credentials.sso.bearer.BearerTokenProvider
import software.amazon.q.jetbrains.utils.extensions.SsoLogin
import software.amazon.q.jetbrains.utils.extensions.SsoLoginExtension

@ExtendWith(ApplicationExtension::class, SsoLoginExtension::class)
@SsoLogin("amazonq-test-account")
class PreAmazonQUiTest {

    @TestDisposable
    lateinit var disposable: Disposable

    @Rule
    @JvmField
    val systemPropertyHelper = SystemPropertyHelper()

    private lateinit var connection: ManagedBearerSsoConnection

    @BeforeEach
    fun setUp() {
        System.setProperty("aws.dev.useDAG", "true")
    }

    @Test
    fun `can set up Connection`() {
        try {
            if (System.getenv("CI").toBoolean()) {
                val startUrl = System.getenv("TEST_START_URL")
                val region = System.getenv("TEST_REGION")
                connection = LegacyManagedBearerSsoConnection(startUrl, region, Q_SCOPES)
                ConnectionPinningManager.getInstance().setPinnedConnection(QConnection.getInstance(), connection)
                (connection.getConnectionSettings().tokenProvider.delegate as BearerTokenProvider).reauthenticate()
            }
        } catch (e: Exception) {
            error("Could not connect to Idc.")
        }
    }
}
