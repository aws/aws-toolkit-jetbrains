package software.aws.toolkits.core.credentials

import assertk.assert
import assertk.assertions.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import software.amazon.awssdk.core.auth.AwsCredentialsProvider

@RunWith(MockitoJUnitRunner::class)
class ToolkitCredentialsManagerTest {

    @Mock lateinit private var providerAlpha: AwsCredentialsProvider
    @Mock lateinit private var providerBeta: AwsCredentialsProvider
    @Mock lateinit private var factoryAlpha: ToolkitCredentialsProviderFactory
    @Mock lateinit private var factoryBeta: ToolkitCredentialsProviderFactory

    private val manager = ToolkitCredentialsManager

    @Before
    fun setUp() {
        Mockito.`when`(factoryAlpha.getAwsCredentialsProvider(startsWith("__alpha"))).thenReturn(providerAlpha)
        Mockito.`when`(factoryBeta.getAwsCredentialsProvider(startsWith("__beta"))).thenReturn(providerBeta)
        manager.reset()
        manager.register(factoryAlpha)
    }

    @Test
    fun testListRegisteredCredentialsProviderFactories() {
        val manager = ToolkitCredentialsManager
        manager.register(factoryAlpha)
        val factories = manager.listRegisteredCredentialsProviderFactories()

        assert(factories).hasSize(1)
        assert(factories).contains(factoryAlpha)
        assert(factories).doesNotContain(factoryBeta)

        manager.register(factoryBeta)
        assert(factories).hasSize(2)
        assert(factories).contains(factoryAlpha)
        assert(factories).contains(factoryBeta)
    }

    @Test
    fun testFindAwsCredentialsProvider_alpha() {
        val manager = ToolkitCredentialsManager
        manager.register(factoryAlpha)
        val mockId = "__alpha_foo"
        val provider = manager.findAwsCredentialsProvider(mockId)
        verify(factoryAlpha).getAwsCredentialsProvider(mockId)
        verify(factoryBeta, never()).getAwsCredentialsProvider(mockId)
        assert(provider).isNotNull()
        assert(provider).isEqualTo(providerAlpha)
    }

    @Test
    fun testFindAwsCredentialsProvider_beta() {
        val manager = ToolkitCredentialsManager
        manager.register(factoryAlpha)
        val mockId = "__beta_foo"
        val provider = manager.findAwsCredentialsProvider(mockId)
        verify(factoryAlpha).getAwsCredentialsProvider(mockId)
        verify(factoryBeta, never()).getAwsCredentialsProvider(mockId)
        assert(provider).isNull()
    }

    @Test
    fun testFindAwsCredentialsProvider_alpha_withBetaRegistered() {
        val mockId = "__alpha_foo"
        manager.register(factoryBeta)
        val provider = manager.findAwsCredentialsProvider(mockId)
        verify(factoryAlpha).getAwsCredentialsProvider(mockId)
        assert(provider).isNotNull()
        assert(provider).isEqualTo(providerAlpha)
    }

    @Test
    fun testFindAwsCredentialsProvider_beta_withBetaRegistered() {
        val mockId = "__beta_foo"
        manager.register(factoryBeta)
        val provider = manager.findAwsCredentialsProvider(mockId)
        verify(factoryBeta).getAwsCredentialsProvider(mockId)
        assert(provider).isNotNull()
        assert(provider).isEqualTo(providerBeta)
    }
}