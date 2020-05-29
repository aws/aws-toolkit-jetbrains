package software.aws.toolkits.jetbrains.core.credentials

import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.TestDataProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.core.AwsResourceCacheTest.Companion.dummyResource
import software.aws.toolkits.jetbrains.core.MockResourceCache


internal class RefreshConnectionActionTest {

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Test
    fun refreshActionClearsCacheAndUpdatesConnectionState() {
        val sut = RefreshConnectionAction()
        val actionEvent = TestActionEvent(TestDataProvider(projectRule.project))

        val states = mutableSetOf<ConnectionState>()
        val mockResourceCache = MockResourceCache.getInstance(projectRule.project)
        val resource = dummyResource()
        mockResourceCache.addEntry(resource, resource.id)

        assertThat(mockResourceCache.entryCount()).isGreaterThan(0)
        projectRule.project.messageBus.connect()
            .subscribe(ProjectAccountSettingsManager.CONNECTION_SETTINGS_STATE_CHANGED, object : ConnectionSettingsStateChangeNotifier {
                override fun settingsStateChanged(newState: ConnectionState) {
                    states.add(newState)
                }
            })

        sut.actionPerformed(actionEvent)

        assertThat(states).hasAtLeastOneElementOfType(ConnectionState.ValidatingConnection::class.java)
    }


}
