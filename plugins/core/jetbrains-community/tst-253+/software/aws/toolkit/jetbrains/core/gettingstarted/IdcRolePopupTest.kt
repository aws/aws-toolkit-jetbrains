// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.core.gettingstarted

import com.intellij.openapi.components.service
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.runInEdtAndWait
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import software.amazon.awssdk.profiles.Profile
import software.amazon.awssdk.services.sso.SsoClient
import software.amazon.awssdk.services.sso.model.RoleInfo
import software.aws.toolkit.core.ToolkitClientManager
import software.aws.toolkit.core.utils.delegateMock
import software.aws.toolkit.core.utils.test.aString
import software.aws.toolkit.jetbrains.core.MockClientManager
import software.aws.toolkit.jetbrains.core.credentials.ConfigFilesFacade
import software.aws.toolkit.jetbrains.utils.satisfiesKt
import software.aws.toolkit.resources.AwsCoreBundle

class IdcRolePopupTest : HeavyPlatformTestCase() {
    private lateinit var mockClientManager: MockClientManager

    override fun setUp() {
        super.setUp()
        mockClientManager = service<ToolkitClientManager>() as MockClientManager

        @Suppress("DEPRECATION")
        mockClientManager.register(SsoClient::class, delegateMock<SsoClient>())
    }

    fun `test validate role selected`() {
        val state = IdcRolePopupState()

        runInEdtAndWait {
            val validation = IdcRolePopup(project, aString(), aString(), mockk(), state, mockk()).run {
                try {
                    performValidateAll()
                } finally {
                    close(0)
                }
            }

            assertThat(validation).singleElement().satisfiesKt {
                assertThat(it.okEnabled).isFalse()
                assertThat(it.message).contains(AwsCoreBundle.message("gettingstarted.setup.error.not_selected"))
            }
        }
    }

    fun `test success writes profile to config`() {
        val sessionName = aString()
        val roleInfo = RoleInfo.builder()
            .roleName(aString())
            .accountId(aString())
            .build()
        val state = IdcRolePopupState().apply {
            this.roleInfo = roleInfo
        }
        val configFilesFacade = mockk<ConfigFilesFacade> {
            every { readAllProfiles() } returns emptyMap()
            justRun { appendProfileToConfig(any()) }
        }

        runInEdtAndWait {
            val sut = IdcRolePopup(
                project,
                region = aString(),
                sessionName = sessionName,
                tokenProvider = mockk(),
                state = state,
                configFilesFacade = configFilesFacade
            )
            try {
                sut.doOkActionWithRoleInfo(roleInfo)
            } finally {
                sut.close(0)
            }

            verify {
                configFilesFacade.appendProfileToConfig(
                    Profile.builder()
                        .name("$sessionName-${roleInfo.accountId()}-${roleInfo.roleName()}")
                        .properties(
                            mapOf(
                                "sso_session" to sessionName,
                                "sso_account_id" to roleInfo.accountId(),
                                "sso_role_name" to roleInfo.roleName()
                            )
                        )
                        .build()
                )
            }
        }
    }
}
