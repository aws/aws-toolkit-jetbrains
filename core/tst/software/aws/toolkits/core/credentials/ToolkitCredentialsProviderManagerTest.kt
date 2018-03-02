package software.aws.toolkits.core.credentials

import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Path

class ToolkitCredentialsProviderManagerTest {

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    @Test fun testLoadEnvironment() {
        val manager = ToolkitCredentialsProviderManager(
                MockToolkitCredentialsProviderRegistry(temporaryFolder.newFile().toPath()),
                MockToolkitCredentialsProviderStore(mapOf(
                        "env" to listOf(mapOf())
                ))
        )
        assertThat(manager.get("env")).isNotNull()
    }

    @Test fun testLoadSystemProperty() {
        val manager = ToolkitCredentialsProviderManager(
                MockToolkitCredentialsProviderRegistry(temporaryFolder.newFile().toPath()),
                MockToolkitCredentialsProviderStore(mapOf(
                        "sys" to listOf(mapOf())
                ))
        )
        assertThat(manager.get("sys")).isNotNull()
    }

    @Test fun testLoadProfile_profileInPersistentFileAndProfileFile() {
        val manager = ToolkitCredentialsProviderManager(
                MockToolkitCredentialsProviderRegistry(File("tst-resources/credentials").toPath()),
                MockToolkitCredentialsProviderStore(mapOf(
                        "profile" to listOf(
                                mapOf("profileName" to "foo")
                        )
                ))
        )
        val fooProvider = manager.get("profile:foo")
        assertThat(fooProvider).isNotNull()
        assertTrue(fooProvider!!.isEnabled())
    }

    @Test fun testLoadProfile_profileNotInPersistentFileButInProfileFile() {
        val manager = ToolkitCredentialsProviderManager(
                MockToolkitCredentialsProviderRegistry(File("tst-resources/credentials").toPath()),
                MockToolkitCredentialsProviderStore(mapOf(
                        "profile" to listOf()
                ))
        )
        val fooProvider = manager.get("profile:foo")
        assertThat(fooProvider).isNotNull()
        assertTrue(fooProvider!!.isEnabled())
    }

    @Test fun testLoadProfile_profileInPersistentDataNotInProfile() {
        val manager = ToolkitCredentialsProviderManager(
                MockToolkitCredentialsProviderRegistry(File("tst-resources/credentials").toPath()),
                MockToolkitCredentialsProviderStore(mapOf(
                        "profile" to listOf(
                                mapOf("profileName" to "baz")
                        )
                ))
        )
        val bazProvider = manager.get("profile:baz")
        assertThat(bazProvider).isNotNull()
        assertFalse(bazProvider!!.isEnabled())
    }

    @Test fun testLoadProfile_invalidPersistentData() {
        val manager = ToolkitCredentialsProviderManager(
                MockToolkitCredentialsProviderRegistry(File("tst-resources/credentials").toPath()),
                MockToolkitCredentialsProviderStore(mapOf(
                        "profile" to listOf(
                                mapOf("profileName1" to "baz")
                        )
                ))
        )
        val bazProvider = manager.get("profile:baz")
        assertThat(bazProvider).isNull()
    }
}

class MockToolkitCredentialsProviderStore(private val data: Map<String, List<Map<String, String>>>)
    : ToolkitCredentialsProviderStore {

    override fun load(): Map<String, List<Map<String, String>>> = data

    override fun save(data: Map<String, List<Map<String, String>>>) {
        // do nothing
    }
}

class MockToolkitCredentialsProviderRegistry(private val profileFileLocation: Path)
    : ToolkitCredentialsProviderRegistry {

    private val factories: Map<String, ToolkitCredentialsProviderFactory> = listOf(
            EnvironmentVariableToolkitCredentialsProviderFactory(),
            SystemPropertyToolkitCredentialsProviderFactory(),
            ProfileToolkitCredentialsProviderFactory(profileFileLocation)
    ).map { it.type to it }.toMap()

    override fun listFactories(): Collection<ToolkitCredentialsProviderFactory> = factories.values

    override fun getFactory(id: String): ToolkitCredentialsProviderFactory? = factories[id]
}