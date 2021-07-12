// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecr

import com.intellij.util.Base64
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.ecr.model.AuthorizationData
import software.aws.toolkits.core.utils.RuleUtils

class EcrUtilsTest {
    @Test
    fun getDockerLogin() {
        val authData: AuthorizationData = mock()
        val user = RuleUtils.randomName()

        whenever(authData.authorizationToken()).thenReturn(Base64.encode("$user:password".toByteArray()))
        val (user1, pass1) = authData.getDockerLogin()
        assertThat(user1).isEqualTo(user)
        assertThat(pass1).isEqualTo("password")

        whenever(authData.authorizationToken()).thenReturn(Base64.encode("$user:::::::password".toByteArray()))
        val (user2, pass2) = authData.getDockerLogin()
        assertThat(user2).isEqualTo(user)
        assertThat(pass2).isEqualTo("::::::password")

        whenever(authData.authorizationToken()).thenReturn(Base64.encode("$user:aGVsbG8=".toByteArray()))
        val (user3, pass3) = authData.getDockerLogin()
        assertThat(user3).isEqualTo(user)
        assertThat(pass3).isEqualTo("aGVsbG8=")
    }
}
