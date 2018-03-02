package software.aws.toolkits.core.credentials

class ToolkitCredentialsProviderManager(
        val registry: ToolkitCredentialsProviderRegistry,
        private val store: ToolkitCredentialsProviderStore
) {
    init {
        store.load().forEach { t, u -> loadFactory(t, u) }
    }

    private fun loadFactory(factoryId: String, data: List<Map<String, String>>) {
        registry.getFactory(factoryId)?.apply {
            data.forEach { this.load(it) }
        }
    }

    fun get(id: String): ToolkitCredentialsProvider? =
            registry.listFactories().mapNotNull { it.get(id) }.firstOrNull()

    /**
     * Serialize and save the current managed TCPs [ToolkitCredentialsProvider].
     */
    fun save() {
        store.save(
                registry.listFactories().map { factory ->
                    factory.type to factory.list().map { tcp -> tcp.toMap() }
                }.toMap()
        )
    }
}

/**
 * Mapping all the [ToolkitCredentialsProvider] to the unmodeled map format, e.g
 * {
 *     "profile": [
 *         {"profileName": "default"},
 *         {"profileName": "foo"}
 *     ],
 *     "env": [
 *         {}
 *     ]
 * }
 */
interface ToolkitCredentialsProviderStore {

    fun load(): Map<String, List<Map<String, String>>>

    fun save(data: Map<String, List<Map<String, String>>>)
}

interface ToolkitCredentialsProviderRegistry {

    fun listFactories(): Collection<ToolkitCredentialsProviderFactory>

    fun getFactory(id: String): ToolkitCredentialsProviderFactory?
}