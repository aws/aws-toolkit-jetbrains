package software.aws.toolkits.core.credentials

class ToolkitCredentialsProviderManager(
        val registry: ToolkitCredentialsProviderRegistry,
        private val store: ToolkitCredentialsProviderStore
) {
    init {
        store.load().forEach { loadTcpFromMap(it) }
    }

    private fun loadTcpFromMap(tcpMap: Map<String, String>) {
        registry.listFactories().forEach { it.load(tcpMap) }
    }

    fun get(id: String): ToolkitCredentialsProvider? =
            registry.listFactories().mapNotNull { it.get(id) }.firstOrNull()
}

interface ToolkitCredentialsProviderStore {

    fun load(): List<Map<String, String>>

    fun save(data: List<Map<String, String>>)
}

interface ToolkitCredentialsProviderRegistry {

    fun listFactories(): Collection<ToolkitCredentialsProviderFactory>

    fun getFactory(id: String): ToolkitCredentialsProviderFactory?
}