// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.FrameWrapper
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.text.DateFormatUtil
import software.aws.toolkits.core.TokenConnectionSettings
import software.aws.toolkits.jetbrains.core.credentials.ToolkitAuthManager
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.sso.DeviceAuthorizationGrantToken
import software.aws.toolkits.jetbrains.core.credentials.sso.PKCEAuthorizationGrantToken
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenProvider
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenProviderListener
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.InteractiveBearerTokenProvider
import software.aws.toolkits.jetbrains.isDeveloperMode
import java.time.Instant

class BearerTokenDebug : DumbAwareAction("BearerTokenDebug") {
    override fun actionPerformed(event: AnActionEvent) {
        BearerTokenDebugDebug().show()
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = isDeveloperMode()
    }

    private class BearerTokenDebugDebug : FrameWrapper(null), BearerTokenProviderListener {
        private val graph = PropertyGraph()
        private val connection = graph.property<ToolkitConnection?>(null)

        init {
            component = createContent()

            ApplicationManager.getApplication().messageBus
                .connect(this)
                .subscribe(BearerTokenProviderListener.TOPIC, this)
        }

        override fun onChange(providerId: String, newScopes: List<String>?) {
            redraw()
        }

        override fun invalidate(providerId: String) {
            redraw()
        }

        private fun createContent() = panel {
            row {
                comboBox(
                    ToolkitAuthManager.getInstance().listConnections() as List<ToolkitConnection?>,
                    SimpleListCellRenderer.create("null") { it?.id }
                )
                    .bindItem(connection)
            }

            row {
                text("Type")
                text("")
                    .applyToComponent {
                        connection.afterChange {
                            this.text = it?.let { it.getConnectionSettings()::class.java.simpleName } ?: "null"
                        }
                    }
            }

            row {
                text("Expires at")
                text("")
                    .applyToComponent {
                        connection.afterChange {
                            val expiresAt = it?.tokenProvider()?.currentToken()?.expiresAt ?: run {
                                this.text = "null"
                                return@afterChange
                            }

                            this.text = DateFormatUtil.formatTime(expiresAt.toEpochMilli())
                        }
                    }
            }

            row("Force expire") {
                button("Refresh Token") {
                    val provider = (connection.get().tokenProvider() as InteractiveBearerTokenProvider)
                    provider.lastToken.getAndUpdate {
                        when (it) {
                            null -> null
                            is DeviceAuthorizationGrantToken -> it.copy(refreshToken = "INVALID_FOR_TESTING", expiresAt = Instant.MIN)
                            is PKCEAuthorizationGrantToken -> it.copy(refreshToken = "INVALID_FOR_TESTING", expiresAt = Instant.MIN)
                        }
                    }
                }

                button("Access Token") {
                    val provider = (connection.get().tokenProvider() as InteractiveBearerTokenProvider)
                    provider.lastToken.getAndUpdate {
                        when (it) {
                            null -> null
                            is DeviceAuthorizationGrantToken -> it.copy(accessToken = "INVALID_FOR_TESTING", expiresAt = Instant.MIN)
                            is PKCEAuthorizationGrantToken -> it.copy(accessToken = "INVALID_FOR_TESTING", expiresAt = Instant.MIN)
                        }
                    }
                }

                button("Both") {
                    val provider = (connection.get().tokenProvider() as InteractiveBearerTokenProvider)
                    provider.lastToken.getAndUpdate {
                        when (it) {
                            null -> null
                            is DeviceAuthorizationGrantToken -> it.copy(
                                accessToken = "INVALID_FOR_TESTING",
                                refreshToken = "INVALID_FOR_TESTING",
                                expiresAt = Instant.MIN
                            )
                            is PKCEAuthorizationGrantToken -> it.copy(
                                accessToken = "INVALID_FOR_TESTING",
                                refreshToken = "INVALID_FOR_TESTING",
                                expiresAt = Instant.MIN
                            )
                        }
                    }
                }

                button("Both and fire message bus") {
                    val provider = (connection.get().tokenProvider() as InteractiveBearerTokenProvider).invalidate()
                }
            }.visibleIf(connection.transform { it.tokenProvider() is InteractiveBearerTokenProvider })
        }

        private fun ToolkitConnection?.tokenProvider() = (this?.getConnectionSettings() as? TokenConnectionSettings)?.tokenProvider?.delegate as? BearerTokenProvider

        private fun redraw() {
            component = createContent()
        }
    }
}
