// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.openapi.project.Project
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.util.xmlb.XmlSerializer
import org.assertj.core.api.Assertions.assertThat
import org.jdom.output.XMLOutputter
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import software.amazon.awssdk.core.pagination.sync.SdkIterable
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.codewhispererruntime.CodeWhispererRuntimeClient
import software.amazon.awssdk.services.codewhispererruntime.model.ListAvailableProfilesRequest
import software.amazon.awssdk.services.codewhispererruntime.model.Profile
import software.amazon.awssdk.services.codewhispererruntime.paginators.ListAvailableProfilesIterable
import software.amazon.awssdk.services.ssooidc.SsoOidcClient
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.jetbrains.core.MockClientManager
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ManagedSsoProfile
import software.aws.toolkits.jetbrains.core.credentials.MockToolkitAuthManagerRule
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.logoutFromSsoConnection
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.core.credentials.sono.Q_SCOPES
import software.aws.toolkits.jetbrains.core.region.MockRegionProviderRule
import software.aws.toolkits.jetbrains.services.amazonq.profile.QEndpoints
import software.aws.toolkits.jetbrains.services.amazonq.profile.QProfileState
import software.aws.toolkits.jetbrains.services.amazonq.profile.QProfileSwitchIntent
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfile
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileManager
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileSelectedListener
import software.aws.toolkits.jetbrains.utils.xmlElement
import java.net.URI
import java.util.function.Consumer
import kotlin.test.fail

// TODO: should use junit5
class QRegionProfileManagerTest {
    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Rule
    @JvmField
    val authRule = MockToolkitAuthManagerRule()

    @JvmField
    @Rule
    val clientRule = MockClientManagerRule()

    @Rule
    @JvmField
    val regionProviderRule = MockRegionProviderRule()

    @JvmField
    @Rule
    val disposableRule = DisposableRule()

    private lateinit var sut: QRegionProfileManager
    private val project: Project
        get() = projectRule.project

    @Before
    fun setup() {
        clientRule.create<SsoOidcClient>()
        regionProviderRule.addRegion(AwsRegion("us-east-1", "US East (N. Virginia)", "aws"))
        regionProviderRule.addRegion(AwsRegion("eu-central-1", "Europe (Frankfurt)", "aws"))
        sut = QRegionProfileManager()
        val conn = authRule.createConnection(ManagedSsoProfile(ssoRegion = "us-east-1", startUrl = "", scopes = Q_SCOPES))
        ToolkitConnectionManager.getInstance(project).switchConnection(conn)
    }

    @Test
    fun `switchProfile should switch the current connection(project) to the selected profile`() {
        sut.switchProfile(project, QRegionProfile(arn = "arn", profileName = "foo_profile"), QProfileSwitchIntent.User)
        assertThat(sut.activeProfile(project)).isEqualTo(QRegionProfile(arn = "arn", profileName = "foo_profile"))

        sut.switchProfile(project, QRegionProfile(arn = "another_arn", profileName = "bar_profile"), QProfileSwitchIntent.User)
        assertThat(sut.activeProfile(project)).isEqualTo(QRegionProfile(arn = "another_arn", profileName = "bar_profile"))
    }

    @Test
    fun `switchProfile should return null if user is not connected`() {
        sut.switchProfile(project, QRegionProfile(arn = "arn", profileName = "foo_profile"), QProfileSwitchIntent.User)
        assertThat(sut.activeProfile(project)).isEqualTo(QRegionProfile(arn = "arn", profileName = "foo_profile"))

        ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance())?.let {
            if (it is AwsBearerTokenConnection) {
                logoutFromSsoConnection(project, it)
            }
        }

        assertThat(ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance())).isNull()
        assertThat(sut.activeProfile(project)).isNull()
    }

    @Test
    fun `switch should send message onProfileChanged for active switch`() {
        var cnt = 0
        project.messageBus.connect(disposableRule.disposable).subscribe(
            QRegionProfileSelectedListener.TOPIC,
            object : QRegionProfileSelectedListener {
                override fun onProfileSelected(project: Project, profile: QRegionProfile?) {
                    cnt += 1
                }
            }
        )

        assertThat(cnt).isEqualTo(0)
        sut.switchProfile(project, QRegionProfile(arn = "arn", profileName = "foo_profile"), QProfileSwitchIntent.Reload)
        assertThat(cnt).isEqualTo(1)
        sut.switchProfile(project, QRegionProfile(arn = "another_arn", profileName = "BAR_PROFILE"), QProfileSwitchIntent.Reload)
        assertThat(cnt).isEqualTo(2)
    }

    @Test
    fun `listProfiles will call each client to get profiles`() {
        val client = clientRule.create<CodeWhispererRuntimeClient>()
        val mockResponse: SdkIterable<Profile> = SdkIterable<Profile> {
            listOf(
                Profile.builder().profileName("FOO").arn("foo").build(),
            ).toMutableList().iterator()
        }

        val mockResponse2: SdkIterable<Profile> = SdkIterable<Profile> {
            listOf(
                Profile.builder().profileName("BAR").arn("bar").build(),
            ).toMutableList().iterator()
        }

        val iterable: ListAvailableProfilesIterable = mock {
            on { it.profiles() } doReturn mockResponse doReturn mockResponse2
        }

        // TODO: not sure if we can mock client with different region different response?
        client.stub {
            onGeneric { listAvailableProfilesPaginator(any<Consumer<ListAvailableProfilesRequest.Builder>>()) } doReturn iterable
        }

        val r = sut.listRegionProfiles(project)
        assertThat(r).hasSize(2)

        assertThat(r).contains(QRegionProfile("FOO", "foo"))
        assertThat(r).contains(QRegionProfile("BAR", "bar"))
    }

    @Test
    fun `validateProfile should cross validate selected profile with latest API response for current project and remove it if its not longer accessible`() {
        val client = clientRule.create<CodeWhispererRuntimeClient>()
        val mockResponse: SdkIterable<Profile> = SdkIterable<Profile> {
            listOf(
                Profile.builder().profileName("foo").arn("foo-arn-v2").build(),
                Profile.builder().profileName("bar").arn("bar-arn").build(),
            ).toMutableList().iterator()
        }
        val iterable: ListAvailableProfilesIterable = mock {
            on { it.profiles() } doReturn mockResponse
        }
        client.stub {
            onGeneric { listAvailableProfilesPaginator(any<Consumer<ListAvailableProfilesRequest.Builder>>()) } doReturn iterable
        }

        val activeConn =
            ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance()) ?: fail("connection shouldn't be null")
        val anotherConn = authRule.createConnection(ManagedSsoProfile(ssoRegion = "us-east-1", startUrl = "anotherUrl", scopes = Q_SCOPES))
        val fooProfile = QRegionProfile("foo", "foo-arn")
        val barProfile = QRegionProfile("bar", "bar-arn")
        val state = QProfileState().apply {
            this.connectionIdToActiveProfile[activeConn.id] = fooProfile
            this.connectionIdToActiveProfile[anotherConn.id] = barProfile
        }
        sut.loadState(state)
        assertThat(sut.activeProfile(project)).isEqualTo(fooProfile)

        sut.validateProfile(project)
        assertThat(sut.activeProfile(project)).isNull()
        assertThat(sut.state.connectionIdToActiveProfile).isEqualTo(mapOf(anotherConn.id to barProfile))
    }

    @Test
    fun `clientSettings should return the region Q profile specify`() {
        MockClientManager.useRealImplementations(disposableRule.disposable)
        sut.switchProfile(
            project,
            QRegionProfile(arn = "arn:aws:codewhisperer:eu-central-1:123456789012:profile/FOO_PROFILE", profileName = "FOO_PROFILE"),
            QProfileSwitchIntent.User
        )
        assertThat(
            sut.activeProfile(project)
        ).isEqualTo(QRegionProfile(arn = "arn:aws:codewhisperer:eu-central-1:123456789012:profile/FOO_PROFILE", profileName = "FOO_PROFILE"))

        val settings = sut.getQClientSettings(project)
        assertThat(settings.region.id).isEqualTo(Region.EU_CENTRAL_1.id())

        sut.switchProfile(
            project,
            QRegionProfile(arn = "arn:aws:codewhisperer:us-east-1:123456789012:profile/BAR_PROFILE", profileName = "BAR_PROFILE"),
            QProfileSwitchIntent.User
        )
        assertThat(
            sut.activeProfile(project)
        ).isEqualTo(QRegionProfile(arn = "arn:aws:codewhisperer:us-east-1:123456789012:profile/BAR_PROFILE", profileName = "BAR_PROFILE"))

        val settings2 = sut.getQClientSettings(project)
        assertThat(settings2.region.id).isEqualTo(Region.US_EAST_1.id())
    }

    @Test
    fun `getClient should return correct client with region and endpoint`() {
        MockClientManager.useRealImplementations(disposableRule.disposable)

        sut.switchProfile(
            project,
            QRegionProfile(arn = "arn:aws:codewhisperer:eu-central-1:123456789012:profile/FOO_PROFILE", profileName = "FOO_PROFILE"),
            QProfileSwitchIntent.User
        )
        assertThat(
            sut.activeProfile(project)
        ).isEqualTo(QRegionProfile(arn = "arn:aws:codewhisperer:eu-central-1:123456789012:profile/FOO_PROFILE", profileName = "FOO_PROFILE"))
        assertThat(sut.getQClientSettings(project).region.id).isEqualTo(Region.EU_CENTRAL_1.id())

        val client = sut.getQClient<CodeWhispererRuntimeClient>(project)
        assertThat(client).isInstanceOf(CodeWhispererRuntimeClient::class.java)
        assertThat(client.serviceClientConfiguration().region()).isEqualTo(Region.EU_CENTRAL_1)
        assertThat(
            client.serviceClientConfiguration().endpointOverride().get()
        ).isEqualTo(URI.create(QEndpoints.getQEndpointWithRegion(Region.EU_CENTRAL_1.id())))

        sut.switchProfile(
            project,
            QRegionProfile(arn = "arn:aws:codewhisperer:us-east-1:123456789012:profile/BAR_PROFILE", profileName = "BAR_PROFILE"),
            QProfileSwitchIntent.User
        )
        assertThat(
            sut.activeProfile(project)
        ).isEqualTo(QRegionProfile(arn = "arn:aws:codewhisperer:us-east-1:123456789012:profile/BAR_PROFILE", profileName = "BAR_PROFILE"))
        assertThat(sut.getQClientSettings(project).region.id).isEqualTo(Region.US_EAST_1.id())

        val client2 = sut.getQClient<CodeWhispererRuntimeClient>(project)
        assertThat(client2).isInstanceOf(CodeWhispererRuntimeClient::class.java)
        assertThat(client2.serviceClientConfiguration().region()).isEqualTo(Region.US_EAST_1)
        assertThat(
            client2.serviceClientConfiguration().endpointOverride().get()
        ).isEqualTo(URI.create(QEndpoints.getQEndpointWithRegion(Region.US_EAST_1.id())))
    }

    @Test
    fun `deserialize empty data`() {
        val element = xmlElement(
            """
                <component name="qProfileStates">
                </component>
                """
        )
        val actual = XmlSerializer.deserialize(element, QProfileState::class.java)
        assertThat(actual.connectionIdToActiveProfile).hasSize(0)
        assertThat(actual.connectionIdToProfileList).hasSize(0)
    }

    @Test
    fun `serialize with data`() {
        val element = xmlElement(
            """
            <component name="qProfileStates">
  </component>
            """.trimIndent()
        )

        val state = QProfileState().apply {
            this.connectionIdToActiveProfile.putAll(
                mapOf(
                    "conn-123" to QRegionProfile(
                        profileName = "myActiveProfile", arn = "arn:aws:codewhisperer:us-west-2:123456789012:profile/myActiveProfile"
                    )
                )
            )

            connectionIdToProfileList.putAll(
                mapOf("conn-123" to 2)
            )
        }

        XmlSerializer.serializeInto(state, element)
        val actualXmlString = XMLOutputter().outputString(element)
        val expectedXmlString =
            "<component name=\"qProfileStates\">\n" +
                "<option name=\"connectionIdToActiveProfile\">" +
                "<map>" +
                "<entry key=\"conn-123\">" +
                "<value>" +
                "<QRegionProfile>" +
                "<option name=\"arn\" value=\"arn:aws:codewhisperer:us-west-2:123456789012:profile/myActiveProfile\" />" +
                "<option name=\"profileName\" value=\"myActiveProfile\" />" +
                "</QRegionProfile>" +
                "</value>" +
                "</entry>" +
                "</map>" +
                "</option>" +
                "<option name=\"connectionIdToProfileList\">" +
                "<map>" +
                "<entry key=\"conn-123\" value=\"2\" />" +
                "</map>" +
                "</option>" +
                "</component>"

        assertThat(actualXmlString).isEqualTo(expectedXmlString)
    }

    @Test
    fun `deserialize with data`() {
        val element = xmlElement(
            """
            <component name="qProfileStates">
              <option name="connectionIdToActiveProfile">
                <map>
                  <entry key="conn-123">
                    <value>
                      <QRegionProfile>
                        <option name="profileName" value="myActiveProfile" />
                        <option name="arn" value="arn:aws:codewhisperer:us-west-2:123456789012:profile/myActiveProfile" />
                      </QRegionProfile>
                    </value>
                  </entry>
                </map>
              </option>
              <option name="connectionIdToProfileList">
                <map>
                  <entry key="conn-123" value = "2" />
                </map>
              </option>
            </component>
            """.trimIndent()
        )

        val actualState = XmlSerializer.deserialize(element, QProfileState::class.java)

        assertThat(actualState.connectionIdToActiveProfile).hasSize(1)
        val activeProfile = actualState.connectionIdToActiveProfile["conn-123"]
        assertThat(activeProfile).isEqualTo(
            QRegionProfile(
                profileName = "myActiveProfile",
                arn = "arn:aws:codewhisperer:us-west-2:123456789012:profile/myActiveProfile"
            )
        )

        assertThat(actualState.connectionIdToProfileList).hasSize(1)
        val profileList = actualState.connectionIdToProfileList["conn-123"]
        assertThat(profileList).isEqualTo(2)
    }
}
