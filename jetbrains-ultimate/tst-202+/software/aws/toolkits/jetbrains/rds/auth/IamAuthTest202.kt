// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

// FIX_WHEN_MIN_IS_202 merge this with the normal one
class IamAuthTest202 {
    @Test
    fun `Valid mysql aurora connection`() {
        val authInformation = iamAuth.getAuthInformation(buildConnection(dbmsType = Dbms.MYSQL_AURORA))
        assertThat(authInformation.port.toString()).isEqualTo(instancePort)
        assertThat(authInformation.user).isEqualTo(username)
        assertThat(authInformation.connectionSettings.region.id).isEqualTo(defaultRegion)
        assertThat(authInformation.address).isEqualTo(dbHost)
    }

    @Test
    fun `No ssl config aurora mysql throws`() {
        assertThatThrownBy { iamAuth.getAuthInformation(buildConnection(dbmsType = Dbms.MYSQL_AURORA, hasSslConfig = false)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage(message("rds.validation.aurora_mysql_ssl_required"))
    }
}
