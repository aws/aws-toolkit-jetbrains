// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials.profiles

import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import software.aws.toolkits.core.rules.SystemPropertyHelper
import software.aws.toolkits.resources.message
import java.io.File

class ProfileReaderTest {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    @Rule
    @JvmField
    val systemPropertyHelper = SystemPropertyHelper()

    private lateinit var configFile: File
    private lateinit var credentialsFile: File

    @Before
    fun setUp() {
        configFile = temporaryFolder.newFile("config")
        credentialsFile = temporaryFolder.newFile("credentials")

        System.getProperties().setProperty("aws.configFile", configFile.absolutePath)
        System.getProperties().setProperty("aws.sharedCredentialsFile", credentialsFile.absolutePath)
    }

    @Test
    fun `source_profile points to a profile that does not exist`() {
        configFile.writeText(
            """
            [profile role]
            role_arn=arn1
            role_session_name=testSession
            source_profile=source_profile
            external_id=externalId
            """.trimIndent()
        )

        val (validProfiles, invalidProfiles) = validateAndGetProfiles()
        assertThat(validProfiles).isEmpty()
        assertThat(invalidProfiles.map { it.key to it.value.message })
            .contains("role" to message("credentials.profile.source_profile_not_found", "role", "source_profile"))
    }

    @Test
    fun `source_profile chain can't go in a circle`() {
        configFile.writeText(
            """
            [profile role]
            role_arn=arn1
            source_profile=source_profile

            [profile source_profile]
            role_arn=arn2
            source_profile=source_profile2

            [profile source_profile2]
            role_arn=arn3
            source_profile=source_profile3

            [profile source_profile3]
            role_arn=arn4
            source_profile=source_profile
            """.trimIndent()
        )

        val (validProfiles, invalidProfiles) = validateAndGetProfiles()
        assertThat(validProfiles).isEmpty()
        assertThat(invalidProfiles.map { it.key to it.value.message })
            .contains(
                "role" to message(
                    "credentials.profile.circular_profiles", "role",
                    "role->source_profile->source_profile2->source_profile3->source_profile"
                )
            )
    }

    @Test
    fun `source_profile can't reference itself`() {
        configFile.writeText(
            """
            [profile role]
            role_arn=arn1
            source_profile=role
            """.trimIndent()
        )

        val (validProfiles, invalidProfiles) = validateAndGetProfiles()
        assertThat(validProfiles).isEmpty()
        assertThat(invalidProfiles.map { it.key to it.value.message })
            .contains("role" to message("credentials.profile.circular_profiles", "role", "role->role"))
    }

    @Test
    fun `assume a role requires either a source_profile or credential_source`() {
        configFile.writeText(
            """
            [profile role]
            role_arn = arn:aws:iam::xxx:role/<role>
            """.trimIndent()
        )

        val (validProfiles, invalidProfiles) = validateAndGetProfiles()
        assertThat(validProfiles).isEmpty()
        assertThat(invalidProfiles.map { it.key to it.value.message })
            .contains("role" to message("credentials.profile.assume_role.missing_source", "role"))
    }

    @Test
    fun `assume a role can't specify both a source_profile or credential_source`() {
        configFile.writeText(
            """
            [profile role]
            role_arn=arn1
            source_profile=source_profile
            credential_source=EcsContainer

            [profile source_profile]
            aws_access_key_id=BarAccessKey
            aws_secret_access_key=BarSecretKey
            """.trimIndent()
        )

        val (validProfiles, invalidProfiles) = validateAndGetProfiles()
        assertThat(validProfiles).containsKey("source_profile")
        assertThat(invalidProfiles.map { it.key to it.value.message })
            .contains("role" to message("credentials.profile.assume_role.duplicate_source", "role"))
    }

    @Test
    fun `credential_source with invalid type is invalid`() {
        configFile.writeText(
            """
            [profile role]
            role_arn=arn1
            credential_source=Foo
            """.trimIndent()
        )

        val (validProfiles, invalidProfiles) = validateAndGetProfiles()
        assertThat(validProfiles).isEmpty()
        assertThat(invalidProfiles.map { it.key to it.value.message })
            .contains("role" to message("credentials.profile.assume_role.invalid_credential_source", "role"))
    }

    @Test
    fun `assume a role with an invalid profile bubbles error`() {
        configFile.writeText(
            """
            [profile role]
            role_arn=arn1
            source_profile=source_profile

            [profile source_profile]
            role_arn=arn2
            """.trimIndent()
        )

        val (validProfiles, invalidProfiles) = validateAndGetProfiles()
        assertThat(validProfiles).isEmpty()
        assertThat(invalidProfiles.map { it.key to it.value.message })
            .contains("role" to message("credentials.profile.assume_role.missing_source", "source_profile"))
            .contains("source_profile" to message("credentials.profile.assume_role.missing_source", "source_profile"))
    }

    @Test
    fun `valid assume role with source_profile returns valid`() {
        configFile.writeText(
            """
            [profile role]
            role_arn=arn1
            source_profile=source_profile

            [profile source_profile]
            aws_access_key_id=BarAccessKey
            aws_secret_access_key=BarSecretKey
            """.trimIndent()
        )

        val (validProfiles, invalidProfiles) = validateAndGetProfiles()
        assertThat(validProfiles).hasSize(2)
        assertThat(invalidProfiles).isEmpty()
    }

    @Test
    fun `valid assume role with credential_source returns valid`() {
        configFile.writeText(
            """
            [profile role]
            role_arn=arn1
            credential_source=EcsContainer
            """.trimIndent()
        )

        val (validProfiles, invalidProfiles) = validateAndGetProfiles()
        assertThat(validProfiles).hasSize(1)
        assertThat(invalidProfiles).isEmpty()
    }

    @Test
    fun `valid assume role with credential_source in a chain returns valid`() {
        configFile.writeText(
            """
            [profile role]
            role_arn=arn1
            source_profile=source_profile

            [profile source_profile]
            role_arn=arn2
            source_profile=source_profile2

            [profile source_profile2]
            role_arn=arn3
            source_profile=source_profile3

            [profile source_profile3]
            role_arn=arn4
            credential_source=EcsContainer
            """.trimIndent()
        )

        val (validProfiles, invalidProfiles) = validateAndGetProfiles()
        assertThat(validProfiles).hasSize(4)
        assertThat(invalidProfiles).isEmpty()
    }
}
