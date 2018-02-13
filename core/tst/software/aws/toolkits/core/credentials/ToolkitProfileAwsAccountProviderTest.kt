package software.aws.toolkits.core.credentials

import assertk.assert
import assertk.assertions.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import software.amazon.awssdk.auth.profile.Profile
import software.amazon.awssdk.auth.profile.ProfileProperties
import software.aws.toolkits.core.credentials.ToolkitAwsAccount.Companion.ACCOUNT_ID
import software.aws.toolkits.core.credentials.ToolkitAwsAccount.Companion.ACCOUNT_NAME
import software.aws.toolkits.core.credentials.ToolkitAwsAccount.Companion.ACCOUNT_PROVIDER_ID
import java.io.File
import java.nio.file.Path

class ToolkitProfileAwsAccountProviderTest {

    private lateinit var expectedProfilePath: Path
    private lateinit var initEmptyProfileAccountProvider: ToolkitProfileAwsAccountProvider
    private lateinit var initNonEmptyProfileAccountProvider: ToolkitProfileAwsAccountProvider

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    @Before
    fun setUp() {
        expectedProfilePath = File("tst-resources/credentials").toPath()
        initEmptyProfileAccountProvider = ToolkitProfileAwsAccountProvider(temporaryFolder.newFile().toPath())
        initNonEmptyProfileAccountProvider = ToolkitProfileAwsAccountProvider(expectedProfilePath)
    }

    @Test
    fun testLoading_withEmptyProfiles() {
        assert(initEmptyProfileAccountProvider.listToolkitAwsAccount()).isEmpty()

        initEmptyProfileAccountProvider.profileFilePath = expectedProfilePath
        assert(initNonEmptyProfileAccountProvider.listToolkitAwsAccount()).hasSize(2)
        assertHasProfile(initNonEmptyProfileAccountProvider, FOO_PROFILE)
        assertHasProfile(initNonEmptyProfileAccountProvider, BAR_PROFILE)
    }

    @Test
    fun testLoading_withExpectedProfiles() {
        assert(initNonEmptyProfileAccountProvider.listToolkitAwsAccount()).hasSize(2)
        assertHasProfile(initNonEmptyProfileAccountProvider, FOO_PROFILE)
        assertHasProfile(initNonEmptyProfileAccountProvider, BAR_PROFILE)

        initNonEmptyProfileAccountProvider.profileFilePath = temporaryFolder.newFile().toPath()
        assert(initEmptyProfileAccountProvider.listToolkitAwsAccount()).isEmpty()
    }

    private fun assertHasProfile(accountProvider: ToolkitAwsAccountProvider, profile: Profile) {
        val account = accountProvider.getToolkitAwsAccountByName(profile.name())
        assert(account).isNotNull()
        val credentialsProvider = account!!.getAwsCredentialsProvider()
        assert(credentialsProvider.credentials.accessKeyId())
                .isEqualTo(profile.property(ProfileProperties.AWS_ACCESS_KEY_ID).get())
        assert(credentialsProvider.credentials.secretAccessKey())
                .isEqualTo(profile.property(ProfileProperties.AWS_SECRET_ACCESS_KEY).get())
    }

    @Test
    fun testCreate() {
        initEmptyProfileAccountProvider.create(FOO_PROFILE)
        assert(initEmptyProfileAccountProvider.listToolkitAwsAccount()).hasSize(1)
        assertHasProfile(initEmptyProfileAccountProvider, FOO_PROFILE)
    }

    @Test
    fun testRemove() {
        val fooAccountId = initEmptyProfileAccountProvider.create(FOO_PROFILE)
        initEmptyProfileAccountProvider.remove(fooAccountId)
        assert(initEmptyProfileAccountProvider.listToolkitAwsAccount()).isEmpty()
    }

    @Test
    fun testUpdate() {
        val accountId = initEmptyProfileAccountProvider.create(FOO_PROFILE)
        initEmptyProfileAccountProvider.update(accountId, BAR_PROFILE)
        assert(initEmptyProfileAccountProvider.getAwsCredentialsProvider(accountId)).isNotNull()
        assert(initEmptyProfileAccountProvider.listToolkitAwsAccount()).hasSize(1)
        assertHasProfile(initEmptyProfileAccountProvider, BAR_PROFILE)
    }

    @Test
    fun testPersistToMap() {
        val accountId = initEmptyProfileAccountProvider.create(FOO_PROFILE)
        val account = initEmptyProfileAccountProvider.getToolkitAwsAccount(accountId)
        assert(account).isNotNull()
        val persistentData = account!!.persistToMap()
        assert(persistentData[ACCOUNT_ID]).isEqualTo(accountId)
        assert(persistentData[ACCOUNT_NAME]).isEqualTo(FOO_PROFILE_NAME)
        assert(persistentData[ACCOUNT_PROVIDER_ID]).isEqualTo(ToolkitProfileAwsAccountProvider.ID)
    }

    @Test
    fun testLoad_persistentDataNotInProfileFile() {
        initEmptyProfileAccountProvider.loadAndStoreToolkitAwsAccount(PERSISTENT_DATA)
        initEmptyProfileAccountProvider.loadAndStoreToolkitAwsAccount(FOO_PERSISTENT_DATA)
        assert(initEmptyProfileAccountProvider.listToolkitAwsAccount()).isEmpty()

        initNonEmptyProfileAccountProvider.loadAndStoreToolkitAwsAccount(PERSISTENT_DATA)
        assert(initNonEmptyProfileAccountProvider.listToolkitAwsAccount()).hasSize(2)
        assert(initNonEmptyProfileAccountProvider.getAwsCredentialsProvider(PERSISTENT_DATA[ACCOUNT_ID]!!)).isNull()
    }

    @Test
    fun testLoad_persistentDataInProfileFile() {
        var fooAccount = initNonEmptyProfileAccountProvider.getToolkitAwsAccountByName(FOO_PROFILE_NAME)
        assert(fooAccount).isNotNull()

        initNonEmptyProfileAccountProvider.loadAndStoreToolkitAwsAccount(FOO_PERSISTENT_DATA)
        assert(initNonEmptyProfileAccountProvider.listToolkitAwsAccount()).hasSize(2)

        fooAccount = initNonEmptyProfileAccountProvider.getToolkitAwsAccount(fooAccount!!.id)
        assert(fooAccount).isNull()

        assert(initNonEmptyProfileAccountProvider.getAwsCredentialsProvider(FOO_PERSISTENT_DATA[ACCOUNT_ID]!!)).isNotNull()
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

        val PERSISTENT_DATA = mapOf(
                ACCOUNT_ID to "some-random-value-1",
                ACCOUNT_NAME to "persistent",
                ACCOUNT_PROVIDER_ID to ToolkitProfileAwsAccountProvider.ID
        )

        val FOO_PERSISTENT_DATA = mapOf(
                ACCOUNT_ID to "some-random-value-foo",
                ACCOUNT_NAME to FOO_PROFILE_NAME,
                ACCOUNT_PROVIDER_ID to ToolkitProfileAwsAccountProvider.ID
        )
    }
}