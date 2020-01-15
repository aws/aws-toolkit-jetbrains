// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials.profiles

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import icons.AwsIcons
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.ProcessCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.profiles.Profile
import software.amazon.awssdk.profiles.ProfileProperty
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest
import software.aws.toolkits.core.ToolkitClientManager
import software.aws.toolkits.core.credentials.ToolkitCredentialsIdentifier
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.core.AwsSdkClient
import software.aws.toolkits.jetbrains.core.credentials.CorrectThreadCredentialsProvider
import software.aws.toolkits.jetbrains.core.credentials.CredentialManager
import software.aws.toolkits.jetbrains.core.credentials.CredentialProviderFactory
import software.aws.toolkits.resources.message
import java.util.function.Supplier

internal class ProfileCredentialsIdentifier(internal val profileName: String) : ToolkitCredentialsIdentifier() {
    override val id = "profile:$profileName"
    override val displayName get() = message("credentials.profile.name", profileName)
}

class ProfileCredentialProviderFactory : CredentialProviderFactory {
    private val profileWatcher = ProfileWatcher().also {
        // TODO: Scope this better in the cred manager refactorDisposer.register(ApplicationManager.getApplication(), it)
    }

    override fun setupToolkitCredentialProviderFactory(manager: CredentialManager) {
        val profiles = ProfileReader().validateAndGetProfiles()

        profileWatcher.start()
    }

    override fun createAwsCredentialProvider(
        providerId: ToolkitCredentialsIdentifier,
        region: AwsRegion,
        sdkClient: AwsSdkClient
    ): AwsCredentialsProvider {
        val profileProviderId = providerId as? ProfileCredentialsIdentifier
            ?: throw IllegalStateException("ProfileCredentialProviderFactory can only handle ProfileCredentialsIdentifier, but got ${providerId::class}")

        val validProfiles = ProfileReader().validateAndGetProfiles().validProfiles
        val rootProfile = validProfiles[profileProviderId.profileName]
            ?: throw IllegalStateException(message("credentials.profile.not_valid", profileProviderId.displayName))

        return createAwsCredentialProvider(validProfiles, rootProfile, region, sdkClient)
    }

    private fun createAwsCredentialProvider(
        validProfiles: Map<ProfileName, Profile>,
        profile: Profile,
        region: AwsRegion,
        sdkClient: AwsSdkClient
    ) = when {
        profile.propertyExists(ProfileProperty.ROLE_ARN) -> createAssumeRoleProvider(validProfiles, profile, region, sdkClient)
        profile.propertyExists(ProfileProperty.AWS_SESSION_TOKEN) -> createStaticSessionProvider(profile)
        profile.propertyExists(ProfileProperty.AWS_ACCESS_KEY_ID) -> createBasicProvider(profile)
        profile.propertyExists(ProfileProperty.CREDENTIAL_PROCESS) -> createCredentialProcessProvider(profile)
        else -> {
            throw IllegalArgumentException(message("credentials.profile.unsupported", profile.name()))
        }
    }

    private fun createAssumeRoleProvider(
        validProfiles: Map<ProfileName, Profile>,
        profile: Profile,
        region: AwsRegion,
        sdkClient: AwsSdkClient
    ): AwsCredentialsProvider {
        val sourceProfileName = profile.requiredProperty(ProfileProperty.SOURCE_PROFILE)
        val sourceProfile = validProfiles[sourceProfileName]
            ?: throw IllegalStateException(message("credentials.profile.not_valid", sourceProfileName))

        // Override the default SPI for getting the active credentials since we are making an internal
        // to this provider client
        val stsClient = ToolkitClientManager.createNewClient(
            StsClient::class,
            sdkClient.sdkHttpClient,
            Region.of(region.id),
            createAwsCredentialProvider(validProfiles, sourceProfile, region, sdkClient),
            AwsClientManager.userAgent
        )

        val roleArn = profile.requiredProperty(ProfileProperty.ROLE_ARN)
        val roleSessionName = profile.property(ProfileProperty.ROLE_SESSION_NAME)
            .orElseGet { "aws-toolkit-jetbrains-${System.currentTimeMillis()}" }
        val externalId = profile.property(ProfileProperty.EXTERNAL_ID)
            .orElse(null)
        val mfaSerial = profile.property(ProfileProperty.MFA_SERIAL)
            .orElse(null)

        return CorrectThreadCredentialsProvider(
            StsAssumeRoleCredentialsProvider.builder()
                .stsClient(stsClient)
                .refreshRequest(Supplier {
                    createAssumeRoleRequest(
                        profile.name(),
                        mfaSerial,
                        roleArn,
                        roleSessionName,
                        externalId
                    )
                })
                .build()
        )
    }

    private fun createAssumeRoleRequest(
        profileName: String,
        mfaSerial: String?,
        roleArn: String,
        roleSessionName: String?,
        externalId: String?
    ): AssumeRoleRequest = AssumeRoleRequest.builder()
        .roleArn(roleArn)
        .roleSessionName(roleSessionName)
        .externalId(externalId).also { request ->
            mfaSerial?.let { _ ->
                request.serialNumber(mfaSerial)
                    .tokenCode(promptMfaToken(profileName, mfaSerial))
            }
        }.build()

    private fun promptMfaToken(name: String, mfaSerial: String): String {
        val result = Ref<String>()

        ApplicationManager.getApplication().invokeAndWait({
            val mfaCode: String = Messages.showInputDialog(
                message("credentials.profile.mfa.message", mfaSerial),
                message("credentials.profile.mfa.title", name),
                AwsIcons.Logos.IAM_LARGE
            ) ?: throw IllegalStateException("MFA challenge is required")

            result.set(mfaCode)
        }, ModalityState.any())

        return result.get()
    }

    private fun createBasicProvider(profile: Profile) = StaticCredentialsProvider.create(
        AwsBasicCredentials.create(
            profile.requiredProperty(ProfileProperty.AWS_ACCESS_KEY_ID),
            profile.requiredProperty(ProfileProperty.AWS_SECRET_ACCESS_KEY)
        )
    )

    private fun createStaticSessionProvider(profile: Profile) = StaticCredentialsProvider.create(
        AwsSessionCredentials.create(
            profile.requiredProperty(ProfileProperty.AWS_ACCESS_KEY_ID),
            profile.requiredProperty(ProfileProperty.AWS_SECRET_ACCESS_KEY),
            profile.requiredProperty(ProfileProperty.AWS_SESSION_TOKEN)
        )
    )

    private fun createCredentialProcessProvider(profile: Profile) = ProcessCredentialsProvider.builder()
        .command(profile.requiredProperty(ProfileProperty.CREDENTIAL_PROCESS))
        .build()
}
