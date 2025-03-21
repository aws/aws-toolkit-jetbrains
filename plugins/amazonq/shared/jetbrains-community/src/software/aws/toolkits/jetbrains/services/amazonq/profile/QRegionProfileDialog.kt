// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.profile

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.panel
import software.aws.toolkits.jetbrains.core.help.HelpIds
import software.aws.toolkits.resources.AmazonQBundle.message
import javax.swing.JComponent

class QRegionProfileDialog(
    private var project: Project,
    private var profiles: List<QRegionProfile>,
    private var selectedProfile: QRegionProfile, // default
) : DialogWrapper(project) {

    private val panel: DialogPanel by lazy {
        panel {
            row { label(message("action.q.switchProfiles.dialog.panel.text")).bold() }
            row { text(message("action.q.switchProfiles.dialog.panel.description")) }
                .bottomGap(BottomGap.MEDIUM)

            buttonsGroup {
                profiles.forEach { profile ->
                    row {
                        radioButton("", profile)

                        panel {
                            row { label("${profile.profileName} - ${profile.region}") }
                            row {
                                label(message("action.q.switchProfiles.dialog.account.label", profile.accountId)).applyToComponent {
                                    font = font.deriveFont(font.size2D - 2.0f)
                                }
                            }
                        }
                    }.bottomGap(BottomGap.MEDIUM)
                }
            }.bind({ selectedOption }, { selectedOption = it })

            separator().bottomGap(BottomGap.MEDIUM)
        }
    }
    private var selectedOption: QRegionProfile = selectedProfile // user selected

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
            QRegionProfileManager.getInstance().switchProfile(project, selectedOption)
        }
        close(OK_EXIT_CODE)
    }
}
