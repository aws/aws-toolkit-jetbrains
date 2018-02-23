package software.aws.toolkits.jetbrains.core.credentials

import com.amazonaws.auth.profile.ProfilesConfigFile
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.get
import com.intellij.util.xmlb.Accessor
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.model.serialization.PathMacroUtil
import software.aws.toolkits.jetbrains.core.credentials.AwsCredentialsProfileProvider.Companion.loadFromCredentialProfile

import software.aws.toolkits.jetbrains.utils.MutableMapWithListener
import java.io.File
import java.nio.file.Paths

interface AwsCredentialsProfileProvider {
    var selectedProfile: CredentialProfile?
    var credentialFileLocation: String?

    companion object {
        const val DEFAULT_PROFILE = "default"

        fun getInstance(project: Project): AwsCredentialsProfileProvider {
            return ServiceManager.getService(project, AwsCredentialsProfileProvider::class.java)
        }

        fun loadFromCredentialProfile(fileLocation: File): MutableMap<String, CredentialProfile> {
            if (!fileLocation.exists()) {
                return mutableMapOf()
            }

            val profilesConfigFile = ProfilesConfigFile(fileLocation)
            profilesConfigFile.refresh()

            return profilesConfigFile.allBasicProfiles.mapValues { CredentialFileBasedProfile(it.value) }.toMutableMap()
        }
    }

    fun getProfiles(): List<CredentialProfile>
    fun lookupProfileByName(name: String): CredentialProfile?
    fun addProfileChangeListener(listener: MutableMapWithListener.MapChangeListener<String, CredentialProfile>)
    fun setProfiles(profiles: List<CredentialProfile>)
}

@State(name = "credentialProfiles", storages = arrayOf(Storage("aws.xml")))
class DefaultAwsCredentialsProfileProvider constructor(private val project: Project) :
        PersistentStateComponent<Element>, AwsCredentialsProfileProvider {
    private data class State(
        var credentialFileLocation: String? = defaultCredentialLocation(),
        var selectedProfileName: String? = null
    )

    private val state = State()
    private val credentialProfiles = MutableMapWithListener<String, CredentialProfile>()

    override var credentialFileLocation: String?
        get() = state.credentialFileLocation
        set(value) {
            state.credentialFileLocation = value
        }

    override var selectedProfile: CredentialProfile?
        get() = credentialProfiles[state.selectedProfileName]
        set(value) {
            state.selectedProfileName = value?.name
        }

    init {
        reloadCredentialFile()
    }

    fun addProfile(profile: CredentialProfile) {
        credentialProfiles.put(profile.name, profile)
    }

    override fun setProfiles(profiles: List<CredentialProfile>) {
        credentialProfiles.clear()
        profiles.forEach { credentialProfiles.put(it.name, it) }

        if (credentialProfiles[state.selectedProfileName] == null) {
            state.selectedProfileName = null
        }
    }

    override fun getProfiles(): List<CredentialProfile> {
        return credentialProfiles.values.toList()
    }

    fun reloadCredentialFile() {
        credentialProfiles.values.removeIf { it is CredentialFileBasedProfile }

        credentialProfiles.putAll(loadFromCredentialProfile(File(state.credentialFileLocation)))
    }

    override fun lookupProfileByName(name: String): CredentialProfile? {
        return credentialProfiles[name]
    }

    override fun addProfileChangeListener(listener: MutableMapWithListener.MapChangeListener<String, CredentialProfile>) {
        credentialProfiles.addListener(listener)
    }

    fun removeProfileChangeListener(listener: MutableMapWithListener.MapChangeListener<String, CredentialProfile>) {
        credentialProfiles.removeListener(listener)
    }

    override fun getState(): Element {
        val serializedState = Element("state")
        XmlSerializer.serializeInto(state, serializedState, object : SkipDefaultValuesSerializationFilters() {
            override fun accepts(accessor: Accessor, bean: Any): Boolean {
                return super.accepts(accessor, bean)
            }
        })

        val serializedProfiles = Element("profiles")
        credentialProfiles.values
                .filterNot { it is CredentialFileBasedProfile }
                .forEach {
                    val profileState = Element("profile")
                    it.save(project, profileState)
                    // Set after so they can't blow it away
                    profileState.setAttribute("id", it.id)
                    profileState.setAttribute("name", it.name)
                    serializedProfiles.addContent(profileState)
                }

        val credentialFileProfiles = credentialProfiles
                .values
                .filterIsInstance<CredentialFileBasedProfile>()
                .map { it.profile }
                .toTypedArray()

        CredentialFileWriter.dumpToFile(File(credentialFileLocation), true, *credentialFileProfiles)

        if (serializedProfiles.contentSize != 0) {
            serializedState.addContent(serializedProfiles)
        }
        return serializedState
    }

    override fun loadState(serializedState: Element) {
        XmlSerializer.deserializeInto(state, serializedState)

        serializedState.get("profiles")?.getChildren("profile")?.forEach {
            val id = it.getAttributeValue("id")
            val name = it.getAttributeValue("name")

            if (id != null && name != null) {
                val factory = CredentialProfileFactory.factoryFor(id)
                if (factory != null) {
                    val credentialProfile = factory.createProvider()
                    credentialProfile.load(project, it)
                    credentialProfile.name = name
                    addProfile(credentialProfile)
                } else {
                    LOG.warn("Failed to find CredentialProfileFactory for type $id")
                }
            }
        }

        reloadCredentialFile()
    }

    @TestOnly
    fun reset() {
        credentialProfiles.clear()
    }

    private companion object {
        val LOG = Logger.getInstance(AwsCredentialsProfileProvider::class.java)

        //TODO: The SDK has its credential locators as internal and returns null instead of the File so if it is a brand new
        // install we won't know where to put them.
        fun defaultCredentialLocation(): String {
            return Paths.get(PathMacroUtil.getUserHomePath(), ".aws", "credentials").toString()
        }
    }
}