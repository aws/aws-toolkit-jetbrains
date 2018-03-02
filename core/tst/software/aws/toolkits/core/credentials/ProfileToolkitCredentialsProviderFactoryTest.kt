package software.aws.toolkits.core.credentials

import assertk.assert
import assertk.assertions.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import software.amazon.awssdk.auth.profile.Profile
import software.amazon.awssdk.auth.profile.ProfileProperties
import software.aws.toolkits.core.credentials.ProfileToolkitCredentialsProvider.Companion.P_PROFILE_NAME
import java.io.File
import java.nio.file.Path

class ProfileToolkitCredentialsProviderFactoryTest {

    private lateinit var expectedProfilePath: Path
    private lateinit var initEmptyProfileAccountProvider: ProfileToolkitCredentialsProviderFactory
    private lateinit var initNonEmptyProfileAccountProvider: ProfileToolkitCredentialsProviderFactory

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    @Before
    fun setUp() {
        expectedProfilePath = File("tst-resources/credentials").toPath()
        initEmptyProfileAccountProvider = ProfileToolkitCredentialsProviderFactory(temporaryFolder.newFile().toPath())
        initNonEmptyProfileAccountProvider = ProfileToolkitCredentialsProviderFactory(expectedProfilePath)
    }

    @Test
    fun testLoading_withEmptyProfiles() {
        assert(initEmptyProfileAccountProvider.list()).isEmpty()

        initEmptyProfileAccountProvider.profileFilePath = expectedProfilePath
        initEmptyProfileAccountProvider.saveToProfileFile()

        assert(initEmptyProfileAccountProvider.list()).hasSize(2)
        assertHasProfile(initEmptyProfileAccountProvider, FOO_PROFILE)
        assertHasProfile(initEmptyProfileAccountProvider, BAR_PROFILE)
    }

    @Test
    fun testLoading_withExpectedProfiles() {
        assert(initNonEmptyProfileAccountProvider.list()).hasSize(2)
        assertHasProfile(initNonEmptyProfileAccountProvider, FOO_PROFILE)
        assertHasProfile(initNonEmptyProfileAccountProvider, BAR_PROFILE)

        initNonEmptyProfileAccountProvider.profileFilePath = temporaryFolder.newFile().toPath()
        assert(initEmptyProfileAccountProvider.list()).isEmpty()
    }

    @Test
    fun testCreate() {
        initEmptyProfileAccountProvider.create(FOO_PROFILE)
        assert(initEmptyProfileAccountProvider.list()).hasSize(1)

        initEmptyProfileAccountProvider.saveToProfileFile()
        assertHasProfile(initEmptyProfileAccountProvider, FOO_PROFILE)
    }

    @Test
    fun testRemove() {
        val fooAccountId = initEmptyProfileAccountProvider.create(FOO_PROFILE)
        initEmptyProfileAccountProvider.remove(fooAccountId.id())
        assert(initEmptyProfileAccountProvider.list()).isEmpty()
    }

    @Test
    fun testUpdate() {
        val tcp = initEmptyProfileAccountProvider.create(FOO_PROFILE)
        initEmptyProfileAccountProvider.add(tcp)

        assert(initEmptyProfileAccountProvider.get(tcp.id())).isNotNull()
        initEmptyProfileAccountProvider.update(tcp.id(), BAR_PROFILE)
        initEmptyProfileAccountProvider.saveToProfileFile()
        assert(initEmptyProfileAccountProvider.list()).hasSize(1)
        assert(initEmptyProfileAccountProvider.get(tcp.id())).isNull()
        assertHasProfile(initEmptyProfileAccountProvider, BAR_PROFILE)
    }

    @Test
    fun testPersistToMap() {
        val accountId = initEmptyProfileAccountProvider.create(FOO_PROFILE)
        val account = initEmptyProfileAccountProvider.get(accountId.id())
        assert(account).isNotNull()
        val persistentData = account!!.toMap()
        assert(persistentData[P_PROFILE_NAME]).isEqualTo(FOO_PROFILE_NAME)
    }

    @Test
    fun testLoad_persistentDataNotInProfileFile() {
        val barProvider = initEmptyProfileAccountProvider.load(PERSISTENT_DATA)
        var fooProvider = initEmptyProfileAccountProvider.load(FOO_PERSISTENT_DATA)
        assert(initEmptyProfileAccountProvider.list()).hasSize(2)

        assert(barProvider).isNotNull()
        assertFalse(barProvider!!.isEnabled())
        assertFalse(fooProvider!!.isEnabled())

        fooProvider = initNonEmptyProfileAccountProvider.load(FOO_PERSISTENT_DATA)
        assert(initNonEmptyProfileAccountProvider.list()).hasSize(2)

        assertFalse(barProvider.isEnabled())
        assertTrue(fooProvider!!.isEnabled())
    }

    @Test
    fun testLoad_persistentDataInProfileFile() {
        var fooAccount = initNonEmptyProfileAccountProvider.getByName(FOO_PROFILE_NAME)
        assert(fooAccount).isNotNull()

        initNonEmptyProfileAccountProvider.load(FOO_PERSISTENT_DATA)
        assert(initNonEmptyProfileAccountProvider.list()).hasSize(2)

        fooAccount = initNonEmptyProfileAccountProvider.get(fooAccount!!.id()) as ProfileToolkitCredentialsProvider
        assert(fooAccount).isNotNull()

        assert(initNonEmptyProfileAccountProvider.getByName(FOO_PERSISTENT_DATA[P_PROFILE_NAME]!!)).isNotNull()
    }

    private fun assertHasProfile(accountProviderFactory: ProfileToolkitCredentialsProviderFactory, profile: Profile) {
        val account = accountProviderFactory.getByName(profile.name())
        assert(account).isNotNull()
        assert(account!!.credentials.accessKeyId())
                .isEqualTo(profile.property(ProfileProperties.AWS_ACCESS_KEY_ID).get())
        assert(account.credentials.secretAccessKey())
                .isEqualTo(profile.property(ProfileProperties.AWS_SECRET_ACCESS_KEY).get())
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
                P_PROFILE_NAME to "persistent"
        )

        val FOO_PERSISTENT_DATA = mapOf(
                P_PROFILE_NAME to FOO_PROFILE_NAME
        )
    }
}