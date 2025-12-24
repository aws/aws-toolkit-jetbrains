// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.profile

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toNullableProperty
import software.amazon.q.jetbrains.core.credentials.AwsBearerTokenConnection
import software.amazon.q.jetbrains.core.credentials.ToolkitConnectionManager
import software.amazon.q.jetbrains.core.credentials.pinning.QConnection
import software.amazon.q.jetbrains.core.help.HelpIds
import software.amazon.q.jetbrains.ui.AsyncComboBox
import software.amazon.q.jetbrains.utils.ui.selected
import software.aws.toolkits.resources.AmazonQBundle.message
import software.amazon.q.resources.AwsCoreBundle
import software.aws.toolkits.telemetry.MetricResult
import software.aws.toolkits.telemetry.Telemetry
import javax.swing.JComponent
import javax.swing.JList

data class QRegionProfileDialogState(
    var selectedProfile: QRegionProfile? = null,
)

class QRegionProfileDialog(
    private var project: Project,
    val state: QRegionProfileDialogState = QRegionProfileDialogState(),
    private var selectedProfile: QRegionProfile?,
) : DialogWrapper(project) {

    private val renderer = object : ColoredListCellRenderer<QRegionProfile>() {
        override fun customizeCellRenderer(
            list: JList<out QRegionProfile>,
            value: QRegionProfile?,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean,
        ) {
            value?.let {
                append(
                    if (it == selectedProfile) {
                        "${it.profileName} - ${it.region} (connected)"
                    } else {
                        "${it.profileName} - ${it.region}"
                    },
                    SimpleTextAttributes.REGULAR_ATTRIBUTES
                )

                append(" " + message("action.q.switchProfiles.dialog.account.label", it.accountId), SimpleTextAttributes.GRAY_SMALL_ATTRIBUTES)
            }
        }
    }

    private val combo = AsyncComboBox<QRegionProfile>(customRenderer = renderer)

    private val panel: DialogPanel by lazy {
        panel {
            row { label(message("action.q.switchProfiles.dialog.panel.text")).bold() }
                .bottomGap(BottomGap.MEDIUM)
            row { text(message("action.q.switchProfiles.dialog.panel.description")) }
            row {
                icon(AllIcons.General.Warning)
                text(message("action.q.switchProfiles.dialog.panel.warning"))
            }
            separator().bottomGap(BottomGap.MEDIUM)

            combo.proposeModelUpdate { model ->
                try {
                    QRegionProfileManager.getInstance().listRegionProfiles(project)?.forEach {
                        model.addElement(it)
                    } ?: error("Attempted to fetch profiles while there does not exist")

                    model.selectedItem = selectedProfile
                } catch (e: Exception) {
                    val conn = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance()) as? AwsBearerTokenConnection
                    Telemetry.amazonq.didSelectProfile.use { span ->
                        span.source(QProfileSwitchIntent.User.value)
                            .amazonQProfileRegion(QRegionProfileManager.getInstance().activeProfile(project)?.region ?: "not-set")
                            .ssoRegion(conn?.region)
                            .credentialStartUrl(conn?.startUrl)
                            .result(MetricResult.Failed)
                            .reason(e.message)
                    }
                    throw e
                }
            }

            row {
                cell(combo)
                    .align(AlignX.FILL)
                    .errorOnApply(AwsCoreBundle.message("gettingstarted.setup.error.not_selected")) { it.selected() == null }
                    .bindItem(state::selectedProfile.toNullableProperty())
            }

            separator().bottomGap(BottomGap.MEDIUM)
        }
    }

    private val selectedOption
        get() = state.selectedProfile // user selected

    init {
        title = message("action.q.switchProfiles.dialog.text")
        setOKButtonText(message("general.ok"))
        setCancelButtonText(message("general.cancel"))
        init()
    }

    override fun getHelpId(): String = HelpIds.Q_SWITCH_PROFILES_DIALOG.id
    override fun createCenterPanel(): JComponent = panel
    override fun doOKAction() {
        panel.apply()
        if (selectedOption != selectedProfile) {
            QRegionProfileManager.getInstance().switchProfile(project, selectedOption, intent = QProfileSwitchIntent.User)
        }
        close(OK_EXIT_CODE)
    }

    override fun doCancelAction() {
        super.doCancelAction()
        val profileManager = QRegionProfileManager.getInstance()
        val conn = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance()) as? AwsBearerTokenConnection
        Telemetry.amazonq.didSelectProfile.use { span ->
            span.source(QProfileSwitchIntent.User.value)
                .amazonQProfileRegion(profileManager.activeProfile(project)?.region ?: "not-set")
                .profileCount(combo.model.size)
                .ssoRegion(conn?.region)
                .credentialStartUrl(conn?.startUrl)
                .result(MetricResult.Cancelled)
        }

        close(CANCEL_EXIT_CODE)
    }
}
