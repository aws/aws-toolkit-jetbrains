// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.toolbox

import com.jetbrains.toolbox.gateway.ProviderVisibilityState
import com.jetbrains.toolbox.gateway.RemoteEnvironmentConsumer
import com.jetbrains.toolbox.gateway.RemoteProvider
import com.jetbrains.toolbox.gateway.ui.CheckboxField
import com.jetbrains.toolbox.gateway.ui.ComboBoxField
import com.jetbrains.toolbox.gateway.ui.LabelField
import com.jetbrains.toolbox.gateway.ui.LinkField
import com.jetbrains.toolbox.gateway.ui.RadioButtonField
import com.jetbrains.toolbox.gateway.ui.TextField
import com.jetbrains.toolbox.gateway.ui.UiField
import com.jetbrains.toolbox.gateway.ui.UiPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.codecatalyst.CodeCatalystClient
import software.amazon.awssdk.services.ssooidc.model.SsoOidcException
import software.aws.toolkits.core.TokenConnectionSettings
import software.aws.toolkits.core.credentials.ToolkitBearerTokenProvider
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.AwsResourceCache
import software.aws.toolkits.jetbrains.core.credentials.ManagedBearerSsoConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitAuthManager
import software.aws.toolkits.jetbrains.core.credentials.reauthProviderIfNeeded
import software.aws.toolkits.jetbrains.core.credentials.sono.CODECATALYST_SCOPES
import software.aws.toolkits.jetbrains.core.credentials.sono.SONO_REGION
import software.aws.toolkits.jetbrains.core.credentials.sono.SONO_URL
import software.aws.toolkits.jetbrains.core.credentials.sono.SonoCredentialManager
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenAuthState
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenProviderListener
import software.aws.toolkits.jetbrains.core.credentials.tokenConnection
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.jetbrains.services.caws.CawsProject
import software.aws.toolkits.jetbrains.services.caws.CawsResources
import software.aws.toolkits.jetbrains.services.caws.listAccessibleProjectsPaginator
import software.aws.toolkits.jetbrains.utils.runUnderProgressIfNeeded
import software.aws.toolkits.resources.message
import java.net.URI
import kotlin.streams.asSequence

class CoCaDevEnvRemoteProvider(
    private val consumer: RemoteEnvironmentConsumer,
    coroutineScope: CoroutineScope
) : RemoteProvider {

    init {
        coroutineScope.launch {
            val tokenProvider = InteractiveBearerTokenProviderNoOpenApi(
                startUrl = SONO_URL,
                region = SONO_REGION,
                scopes = CODECATALYST_SCOPES
            )

            val state = tokenProvider.state()
            when (state) {
                BearerTokenAuthState.NOT_AUTHENTICATED -> {
                    tokenProvider.reauthenticate()
                }

                BearerTokenAuthState.NEEDS_REFRESH -> {
                    try {
                        tokenProvider.resolveToken()
                    } catch (e: SsoOidcException) {
                        tokenProvider.reauthenticate()
                    }
                }

                BearerTokenAuthState.AUTHORIZED -> { }
            }

            val client = CodeCatalystClient.builder()
                .region(Region.AWS_GLOBAL)
                .endpointOverride(URI("https://public.codecatalyst.global.api.aws"))
                .tokenProvider(tokenProvider)
                .build()

            sequence {
                client.listSpacesPaginator {}
                    .items()
                    .stream()
                    .asSequence()
                    .forEach {
                        println(it)
                        val space = it.name()
                        yieldAll(
                            client.listAccessibleProjectsPaginator { it.spaceName(space) }
                                .items()
                                .map { CawsProject(space = space, project = it.name()) }
                        )
                    }
            }.map {
                CoCaDevEnvRemoteEnvironment(it)
            }.let {
                consumer.consumeEnvironments(listOf(it.first()))
            }
        }
    }

    override fun getNewEnvironmentUiPage(): UiPage {
        return object : UiPage {
            override fun getTitle(): String {
                return "UiPage Title"
            }

            override fun getFields(): MutableList<UiField> =
                mutableListOf(
                    CheckboxField(true, "CheckboxField"),
                    ComboBoxField("ComboBoxField", "ComboBoxField", listOf(ComboBoxField.LabelledValue("ComboBoxField", "ComboBoxField"))),
                    LabelField("LabelField"),
                    LinkField("LinkField", "https://example.com"),
                    RadioButtonField(true, "RadioButtonField", "RadioButtonField"),
                    TextField("TextField")
                )
        }
    }

    override fun close() {}

    override fun getName(): String {
        return "CoCaRemoteProvider"
    }

    override fun canCreateNewEnvironments(): Boolean = true

    override fun isSingleEnvironment(): Boolean = false

    override fun setVisibilityState(visibilityState: ProviderVisibilityState) {
    }

    override fun addEnvironmentsListener(listener: RemoteEnvironmentConsumer?) {
    }

    override fun removeEnvironmentsListener(listener: RemoteEnvironmentConsumer?) {
    }

    override fun handleUri(uri: URI) {
        println("handleUri: $uri")
    }
}
