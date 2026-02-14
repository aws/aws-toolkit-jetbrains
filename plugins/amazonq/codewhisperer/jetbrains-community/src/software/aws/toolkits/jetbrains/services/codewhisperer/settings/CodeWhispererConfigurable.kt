// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.settings

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.emptyText
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.execution.ParametersListUtil
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.DidChangeConfigurationParams
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManagerListener
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.core.credentials.sono.isInternalUser
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererLoginType
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExplorerActionManager
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.isCodeWhispererEnabled
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import software.aws.toolkits.jetbrains.settings.LspSettings
import software.aws.toolkits.resources.message
import java.awt.Font
import java.util.concurrent.TimeUnit

//  As the connection is project-level, we need to make this project-level too (we have different config for Sono vs SSO users)
class CodeWhispererConfigurable(private val project: Project) :
    BoundConfigurable(message("aws.settings.codewhisperer.configurable.title")),
    SearchableConfigurable {
    private val codeWhispererSettings
        get() = CodeWhispererSettings.getInstance()

    private val isSso: Boolean
        get() = CodeWhispererExplorerActionManager.getInstance().checkActiveCodeWhispererConnectionType(project) == CodeWhispererLoginType.SSO

    private val isInternalUser: Boolean
        get() {
            val conn = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance()) as? AwsBearerTokenConnection
            return conn?.let { isInternalUser(it.startUrl) } ?: false
        }

    override fun getId() = "aws.codewhisperer"

    override fun createPanel() = panel {
        val connect = project.messageBus.connect(disposable ?: error("disposable wasn't initialized by framework"))
        val invoke = isCodeWhispererEnabled(project)

        // TODO: can we remove message bus subscribe and solely use visible(boolean) / enabled(boolean), consider multi project cases
        row {
            label(message("aws.settings.codewhisperer.warning")).apply {
                component.icon = AllIcons.General.Warning
            }.apply {
                visible(!invoke)
                connect.subscribe(
                    ToolkitConnectionManagerListener.TOPIC,
                    object : ToolkitConnectionManagerListener {
                        override fun activeConnectionChanged(newConnection: ToolkitConnection?) {
                            visible(!isCodeWhispererEnabled(project))
                        }
                    }
                )
            }
        }

        group(message("amazonqFeatureDev.placeholder.lsp")) {
            row(message("amazonqFeatureDev.placeholder.select_lsp_artifact")) {
                val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
                fileChooserDescriptor.isForcedToUseIdeaFileChooser = true

                textFieldWithBrowseButton(fileChooserDescriptor = fileChooserDescriptor)
                    .bindText(
                        { LspSettings.getInstance().getArtifactPath().orEmpty() },
                        { LspSettings.getInstance().setArtifactPath(it) }
                    )
                    .applyToComponent {
                        emptyText.text = message("executableCommon.auto_managed")
                    }
                    .resizableColumn()
                    .align(Align.FILL)
            }
            row(message("amazonqFeatureDev.placeholder.node_runtime_path")) {
                val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
                fileChooserDescriptor.isForcedToUseIdeaFileChooser = true

                textFieldWithBrowseButton(fileChooserDescriptor = fileChooserDescriptor)
                    .bindText(
                        { LspSettings.getInstance().getNodeRuntimePath().orEmpty() },
                        { LspSettings.getInstance().setNodeRuntimePath(it) }
                    )
                    .applyToComponent {
                        emptyText.text = message("executableCommon.auto_managed")
                    }
                    .resizableColumn()
                    .align(Align.FILL)
            }
            row {
                checkBox("Enable CPU profiling")
                    .bindSelected(
                        { LspSettings.getInstance().isCpuProfilingEnabled() },
                        { LspSettings.getInstance().setCpuProfilingEnabled(it) }
                    )
                    .comment("Enable CPU profiling for the LSP server to help diagnose performance issues")
            }
        }

        group(message("aws.settings.codewhisperer.group.general")) {
            row {
                checkBox(message("aws.settings.codewhisperer.include_code_with_reference")).apply {
                    connect.subscribe(
                        ToolkitConnectionManagerListener.TOPIC,
                        object : ToolkitConnectionManagerListener {
                            override fun activeConnectionChanged(newConnection: ToolkitConnection?) {
                                enabled(isCodeWhispererEnabled(project) && !isSso)
                            }
                        }
                    )
                    enabled(invoke && !isSso)
                    bindSelected(codeWhispererSettings::isIncludeCodeWithReference, codeWhispererSettings::toggleIncludeCodeWithReference)
                }.comment(message("aws.settings.codewhisperer.include_code_with_reference.tooltip"))
                if (isSso) {
                    label(message("aws.settings.codewhisperer.configurable.controlled_by_admin")).applyToComponent {
                        font = font.deriveFont(Font.ITALIC).deriveFont((font.size - 1).toFloat())
                    }.enabled(false)
                }
            }

            row {
                checkBox(message("aws.settings.codewhisperer.workspace_context")).apply {
                    connect.subscribe(
                        ToolkitConnectionManagerListener.TOPIC,
                        object : ToolkitConnectionManagerListener {
                            override fun activeConnectionChanged(newConnection: ToolkitConnection?) {
                                enabled(isCodeWhispererEnabled(project))
                            }
                        }
                    )
                    enabled(invoke)
                    bindSelected(codeWhispererSettings::isWorkspaceContextEnabled, codeWhispererSettings::toggleWorkspaceContextEnabled)
                }.comment(message("aws.settings.codewhisperer.workspace_context.tooltip"))
            }.visible(isInternalUser)
        }

        group(message("aws.settings.codewhisperer.group.inline_suggestions")) {
            row {
                checkBox(message("aws.settings.codewhisperer.automatic_import_adder")).apply {
                    connect.subscribe(
                        ToolkitConnectionManagerListener.TOPIC,
                        object : ToolkitConnectionManagerListener {
                            override fun activeConnectionChanged(newConnection: ToolkitConnection?) {
                                enabled(isCodeWhispererEnabled(project))
                            }
                        }
                    )
                    enabled(invoke)
                    bindSelected(codeWhispererSettings::isImportAdderEnabled, codeWhispererSettings::toggleImportAdder)
                }.comment(message("aws.settings.codewhisperer.automatic_import_adder.tooltip"))
            }

            row {
                link("Configure inline suggestion keybindings") { e ->
                    // TODO: user needs feedback if these are null
                    val settings = DataManager.getInstance().getDataContext(e.source as ActionLink).getData(Settings.KEY) ?: return@link
                    val configurable: Configurable = settings.find("preferences.keymap") ?: return@link

                    settings.select(configurable, Q_INLINE_KEYBINDING_SEARCH_TEXT)

                    // workaround for certain cases for sometimes the string is not input there
                    EdtExecutorService.getScheduledExecutorInstance().schedule({
                        settings.select(configurable, Q_INLINE_KEYBINDING_SEARCH_TEXT)
                    }, 500, TimeUnit.MILLISECONDS)
                }
            }
        }

        group(message("aws.settings.codewhisperer.group.q_chat")) {
            row {
                checkBox(message("aws.settings.codewhisperer.project_context")).apply {
                    connect.subscribe(
                        ToolkitConnectionManagerListener.TOPIC,
                        object : ToolkitConnectionManagerListener {
                            override fun activeConnectionChanged(newConnection: ToolkitConnection?) {
                                enabled(isCodeWhispererEnabled(project))
                            }
                        }
                    )
                    enabled(invoke)
                    bindSelected(codeWhispererSettings::isProjectContextEnabled, codeWhispererSettings::toggleProjectContextEnabled)
                }.comment(message("aws.settings.codewhisperer.project_context.tooltip"))
            }

            row(message("aws.settings.codewhisperer.project_context_index_thread")) {
                intTextField(
                    range = CodeWhispererSettings.CONTEXT_INDEX_THREADS
                ).bindIntText(codeWhispererSettings::getProjectContextIndexThreadCount, codeWhispererSettings::setProjectContextIndexThreadCount)
                    .apply {
                        connect.subscribe(
                            ToolkitConnectionManagerListener.TOPIC,
                            object : ToolkitConnectionManagerListener {
                                override fun activeConnectionChanged(newConnection: ToolkitConnection?) {
                                    enabled(isCodeWhispererEnabled(project))
                                }
                            }
                        )
                        enabled(invoke)
                    }.comment(message("aws.settings.codewhisperer.project_context_index_thread.tooltip"))
            }

            row(message("aws.settings.codewhisperer.project_context_index_max_size")) {
                intTextField(
                    range = CodeWhispererSettings.CONTEXT_INDEX_SIZE
                ).bindIntText(codeWhispererSettings::getProjectContextIndexMaxSize, codeWhispererSettings::setProjectContextIndexMaxSize)
                    .apply {
                        connect.subscribe(
                            ToolkitConnectionManagerListener.TOPIC,
                            object : ToolkitConnectionManagerListener {
                                override fun activeConnectionChanged(newConnection: ToolkitConnection?) {
                                    enabled(isCodeWhispererEnabled(project))
                                }
                            }
                        )
                        enabled(invoke)
                    }.comment(message("aws.settings.codewhisperer.project_context_index_max_size.tooltip"))
            }

            row {
                checkBox(message("aws.settings.codewhisperer.project_context_gpu")).apply {
                    connect.subscribe(
                        ToolkitConnectionManagerListener.TOPIC,
                        object : ToolkitConnectionManagerListener {
                            override fun activeConnectionChanged(newConnection: ToolkitConnection?) {
                                enabled(isCodeWhispererEnabled(project))
                            }
                        }
                    )
                    enabled(invoke)
                    bindSelected(codeWhispererSettings::isProjectContextGpu, codeWhispererSettings::toggleProjectContextGpu)
                }.comment(message("aws.settings.codewhisperer.project_context_gpu.tooltip"))
            }
        }

        val autoBuildSetting = codeWhispererSettings.getAutoBuildSetting()
        if (autoBuildSetting.isNotEmpty()) {
            group(message("aws.settings.codewhisperer.feature_development")) {
                row {
                    text(message("aws.settings.codewhisperer.feature_development.allow_running_code_and_test_commands"))
                }
                row {
                    val settings = codeWhispererSettings.getAutoBuildSetting()
                    for ((key) in settings) {
                        checkBox(key).apply {
                            connect.subscribe(
                                ToolkitConnectionManagerListener.TOPIC,
                                object : ToolkitConnectionManagerListener {
                                    override fun activeConnectionChanged(newConnection: ToolkitConnection?) {
                                        enabled(isCodeWhispererEnabled(project))
                                    }
                                }
                            )

                            bindSelected(
                                getter = { codeWhispererSettings.isAutoBuildFeatureEnabled(key) },
                                setter = { newValue -> codeWhispererSettings.toggleAutoBuildFeature(key, newValue) }
                            )
                        }
                    }
                }
            }
        }

        group(message("aws.settings.codewhisperer.code_review")) {
            row {
                ExpandableTextField(ParametersListUtil.COLON_LINE_PARSER, ParametersListUtil.COLON_LINE_JOINER).also {
                    cell(it)
                        .label(message("aws.settings.codewhisperer.code_review.title"))
                        .comment(message("aws.settings.codewhisperer.code_review.description"))
                        .bindText(codeWhispererSettings::getIgnoredCodeReviewIssues, codeWhispererSettings::setIgnoredCodeReviewIssues)
                }
            }
        }

        group(message("aws.settings.codewhisperer.group.data_sharing")) {
            row {
                checkBox(message("aws.settings.codewhisperer.configurable.opt_out.title")).apply {
                    connect.subscribe(
                        ToolkitConnectionManagerListener.TOPIC,
                        object : ToolkitConnectionManagerListener {
                            override fun activeConnectionChanged(newConnection: ToolkitConnection?) {
                                enabled(isCodeWhispererEnabled(project))
                            }
                        }
                    )

                    enabled(invoke)

                    bindSelected(codeWhispererSettings::isMetricOptIn, codeWhispererSettings::toggleMetricOptIn)
                }.comment(message("aws.settings.codewhisperer.configurable.opt_out.tooltip"))
            }
        }
    }.also {
        val newCallbacks = it.applyCallbacks.toMutableMap()
            .also { map ->
                val list = map.getOrPut(null) { mutableListOf() } as MutableList<() -> Unit>
                list.add {
                    ProjectManager.getInstance().openProjects.forEach { project ->
                        if (project.isDisposed) {
                            return@forEach
                        }

                        currentThreadCoroutineScope().launch {
                            AmazonQLspService.executeAsyncIfRunning(project) { server ->
                                server.workspaceService.didChangeConfiguration(DidChangeConfigurationParams())
                            }
                        }
                    }
                }
            }
        it.applyCallbacks = newCallbacks
    }

    companion object {
        private const val Q_INLINE_KEYBINDING_SEARCH_TEXT = "Inline Proposal"
    }
}
