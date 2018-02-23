package software.aws.toolkits.core.credentials

import assertk.assert
import assertk.assertions.*
import org.junit.Test
import software.amazon.awssdk.auth.profile.Profile
import software.amazon.awssdk.auth.profile.ProfileProperties
import java.io.File

class ToolkitProfileCredentialsProviderFactoryTest {

    private val profileProviderFactory = ToolkitProfileCredentialsProviderFactory
    private val expectedProfilePath = File("tst-resources/credentials").toPath()

    @Test fun testLoading() {
        profileProviderFactory.profileFilePath = expectedProfilePath

        assert(profileProviderFactory.listAwsToolkitCredentialsProviders()).hasSize(2)
        assertHasCredentials(profileProviderFactory, FOO_PROFILE_NAME, FOO_ACCESS_KEY, FOO_SECRET_KEY)
        assertHasCredentials(profileProviderFactory, BAR_PROFILE_NAME, BAR_ACCESS_KEY, BAR_SECRET_KEY)
    }

    private fun assertHasCredentials(factory: ToolkitCredentialsProviderFactory, profileName: String, accessKey: String, secretKey: String) {
        val provider = factory.getAwsCredentialsProvider(profileName)
        assert(provider).isNotNull()
        assert(provider?.credentials).isNotNull()
        assert(provider?.credentials?.accessKeyId()).isEqualTo(accessKey)
        assert(provider?.credentials?.secretAccessKey()).isEqualTo(secretKey)
    }

    @Test fun testEditingProfiles() {
        val profilePath = File.createTempFile("credentials", "txt").toPath()

        profileProviderFactory.profileFilePath = profilePath
        assert(profileProviderFactory.listAwsToolkitCredentialsProviders()).isEmpty()

        profileProviderFactory.create(FOO_PROFILE_NAME, FOO_PROFILE)
        profileProviderFactory.save()

        assert(profileProviderFactory.listAwsToolkitCredentialsProviders()).hasSize(1)
        assertHasCredentials(profileProviderFactory, FOO_PROFILE_NAME, FOO_ACCESS_KEY, FOO_SECRET_KEY)

        profileProviderFactory.update(FOO_PROFILE_NAME, BAR_PROFILE)
        profileProviderFactory.save()

        assert(profileProviderFactory.listAwsToolkitCredentialsProviders()).hasSize(1)
        assert(profileProviderFactory.getAwsCredentialsProvider(FOO_PROFILE_NAME)).isNull()
        assertHasCredentials(profileProviderFactory, BAR_PROFILE_NAME, BAR_ACCESS_KEY, BAR_SECRET_KEY)

        profileProviderFactory.remove(BAR_PROFILE_NAME)
        profileProviderFactory.save()

        assert(profileProviderFactory.listAwsToolkitCredentialsProviders()).isEmpty()
        assert(profileProviderFactory.getAwsCredentialsProvider(BAR_PROFILE_NAME)).isNull()
    }

    @Test fun testLoadingFromInvalidPath() {
        profileProviderFactory.profileFilePath = File("none/existing/path").toPath()
        assert(profileProviderFactory.listAwsToolkitCredentialsProviders()).isEmpty()
    }

    companion object {
        const val FOO_PROFILE_NAME = "foo"
        const val FOO_ACCESS_KEY = "FooAccessKey"
        const val FOO_SECRET_KEY = "FooSecretKey"

        const val BAR_PROFILE_NAME = "bar"
        const val BAR_ACCESS_KEY = "BarAccessKey"
        const val BAR_SECRET_KEY = "BarSecretKey"

        val FOO_PROFILE = Profile.builder()
                .name(FOO_PROFILE_NAME)
                .properties(mapOf(
                        ProfileProperties.AWS_ACCESS_KEY_ID to FOO_ACCESS_KEY,
                        ProfileProperties.AWS_SECRET_ACCESS_KEY to FOO_SECRET_KEY
                ))
                .build()

        val BAR_PROFILE = Profile.builder()
                .name(BAR_PROFILE_NAME)
                .properties(mapOf(
                        ProfileProperties.AWS_ACCESS_KEY_ID to BAR_ACCESS_KEY,
                        ProfileProperties.AWS_SECRET_ACCESS_KEY to BAR_SECRET_KEY
                ))
                .build()
    }
}