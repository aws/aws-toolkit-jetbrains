// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.gettingstarted

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.AwsIcons
import software.aws.toolkits.jetbrains.services.caws.CawsEndpoints
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererColorUtil
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.CODEWHISPERER_LEARN_MORE_URI
import software.aws.toolkits.jetbrains.ui.feedback.FeedbackDialog
import software.aws.toolkits.resources.message
import java.awt.Color
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JComponent

open class OuterPanel(
    private val project: Project
) : DialogWrapper(project) {

    private val panel: DialogPanel by lazy { createPanel() }
    init {

        init()
    }

    fun createPanel() = panel {
        row {
            apply {
                icon(AwsIcons.Logos.AWS_SMILE_LARGE)
            }
            panel {
                row {
                    label(message("aws.onboarding.getstarted.panel.title")).applyToComponent {
                        font = JBFont.h1().asBold()
                        foreground = Paneltext.TITLE_TEXT_FONTCOLOR
                    }
                }
                    .rowComment(
                        message("aws.onboarding.getstarted.panel.comment_link", "<a>${message("aws.onboarding.getstarted.panel.share_feedback")}")
                    ) { hyperlinkEvent ->
                        close(OK_EXIT_CODE)
                        val actionEvent = AnActionEvent.createFromInputEvent(
                            hyperlinkEvent.inputEvent,
                            Paneltext.SHARE_FEEDBACK_LINK,
                            null
                        ) { if (PlatformDataKeys.PROJECT.`is`(it)) project else null }
                        ActionManager.getInstance().getAction("aws.toolkit.getstarted.shareFeedback").actionPerformed(actionEvent)
                    }
            }
        }

        group(message("aws.onboarding.getstarted.panel.group_title", "#FFFFFF")) {
            row {
                // CodeWhisperer panel
                cell(CodeWhispererPanel())
                // Resource Explorer Panel
                cell(ResourceExplorerPanel())
                // CodeCatalyst Panel
                cell(CodeCatalystPanel())
            }
        }

        collapsibleGroup(message("aws.onboarding.getstarted.panel.bottom_text_question")) {
            row {
                text(message("aws.onboarding.getstarted.panel.bottom_text"))
            }
            row {
                // CodeWhisperer auth bullets
                cell(CodeWhispererAuthBullets(message("codewhisperer.experiment")))
                // Resource Explorer panel auth bullets
                cell(ResourceExplorerAuthBullets(message("aws.getstarted.resource.panel_title")))
                // CodeCatalyst panel auth bullets
                cell(CodeCatalystAuthBullets(message("caws.devtoolPanel.title")))
            }
        }
    }
    override fun createCenterPanel(): JComponent = panel
}

class CodeCatalystPanel : BorderLayoutPanel() {
    init {
        addToCenter(
            panel {
                indent {
                    row {
                        label(message("caws.devtoolPanel.title")).bold()
                            .applyToComponent {
                                foreground = Paneltext.TITLE_TEXT_FONTCOLOR
                            }
                    }

                    row {
                        panel {
                            row {
                                label("").applyToComponent {
                                    border = BorderFactory.createLineBorder(Paneltext.TEXT_FONTCOLOR)
                                    preferredSize = Dimension(250, 110)
                                }
                            }
                        }
                    }

                    row {
                        text(message("caws.getstarted.panel.description")).applyToComponent { foreground = Paneltext.TEXT_FONTCOLOR }
                    }

                    row {
                        browserLink(message("codewhisperer.gettingstarted.panel.learn_more"), CawsEndpoints.ConsoleFactory.baseUrl())
                    }

                    row {
                        button(message("caws.getstarted.panel.login")) {}.apply {
                            applyToComponent {
                                putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
                            }
                        }
                        // topGap(TopGap.SMALL)
                    }

                    row {
                        label(message("caws.getstarted.panel.question.text")).applyToComponent { foreground = Paneltext.TEXT_FONTCOLOR }
                    }
                    row {
                        browserLink(message("caws.getstarted.panel.link_text"), url = CODEWHISPERER_LEARN_MORE_URI)
                    }
                }
            }
        )

        border = IdeBorderFactory.createRoundedBorder().apply {
            setColor(CodeWhispererColorUtil.POPUP_BUTTON_BORDER)
            preferredSize = Dimension(Paneltext.PANEL_WIDTH, Paneltext.PANEL_HEIGHT)
        }
    }
}

class ResourceExplorerPanel : BorderLayoutPanel() {
    init {
        addToCenter(
            panel {
                indent {
                    row {
                        label(message("aws.getstarted.resource.panel_title")).bold()
                            .applyToComponent {
                                foreground = Paneltext.TITLE_TEXT_FONTCOLOR
                            }
                    }
                    row {
                        panel {
                            row {
                                label("Image").applyToComponent {
                                    border = BorderFactory.createLineBorder(Paneltext.TEXT_FONTCOLOR)
                                    preferredSize = Dimension(250, 110)
                                }
                            }
                        }
                    }

                    row {
                        text(message("aws.getstarted.resource.panel_description")).applyToComponent { foreground = Paneltext.TEXT_FONTCOLOR }
                    }

                    row {
                        browserLink(
                            message("codewhisperer.gettingstarted.panel.learn_more"),
                            url = "https://docs.aws.amazon.com/toolkit-for-jetbrains/latest/userguide/working-with-aws.html"
                        )
                    }

                    row {
                        button(message("aws.onboarding.getstarted.panel.button_iam_login")) {}.apply {
                            applyToComponent {
                                putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
                            }
                        }
                        topGap(TopGap.MEDIUM)
                    }

                    row {
                        label(message("aws.getstarted.resource.panel_question_text")).applyToComponent { foreground = Paneltext.TEXT_FONTCOLOR }
                    }
                    row {
                        browserLink(message("aws.onboarding.getstarted.panel.signup_iam_text"), url = "https://aws.amazon.com/free/")
                    }
                }
            }
        )

        border = IdeBorderFactory.createRoundedBorder().apply {
            setColor(CodeWhispererColorUtil.POPUP_BUTTON_BORDER)
            preferredSize = Dimension(Paneltext.PANEL_WIDTH, Paneltext.PANEL_HEIGHT)
        }
    }
}

class CodeWhispererPanel : BorderLayoutPanel() {
    init {
        addToCenter(
            panel {
                indent {
                    row {
                        label(message("codewhisperer.experiment")).bold()
                            .applyToComponent { foreground = Paneltext.TITLE_TEXT_FONTCOLOR }
                    }
                    row {
                        panel {
                            row {
                                label("Image").applyToComponent {
                                    border = BorderFactory.createLineBorder(Paneltext.TEXT_FONTCOLOR)
                                    preferredSize = Dimension(250, 110)
                                }
                            }
                        }
                    }

                    row {
                        text(message("codewhisperer.gettingstarted.panel.comment")).applyToComponent { foreground = Paneltext.TEXT_FONTCOLOR }
                    }

                    row {
                        browserLink(message("codewhisperer.gettingstarted.panel.learn_more"), url = CODEWHISPERER_LEARN_MORE_URI)
                    }

                    row {
                        button(message("codewhisperer.gettingstarted.panel.login_button")) {}.apply {
                            applyToComponent {
                                putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
                            }
                        }
                        topGap(TopGap.SMALL)
                    }

                    row {
                        label(message("codewhisperer.gettingstarted.panel.licence_comment")).applyToComponent { foreground = Paneltext.TEXT_FONTCOLOR }
                    }
                    row {
                        browserLink(message("aws.onboarding.getstarted.panel.login_with_iam"), url = CODEWHISPERER_LEARN_MORE_URI)
                    }
                }
            }
        )

        border = IdeBorderFactory.createRoundedBorder().apply {
            setColor(CodeWhispererColorUtil.POPUP_BUTTON_BORDER)

            roundedCorners
            preferredSize = Dimension(Paneltext.PANEL_WIDTH, Paneltext.PANEL_HEIGHT)
        }
    }
}

class ShareFeedbackInGetStarted : DumbAwareAction() {

    override fun actionPerformed(e: AnActionEvent) {
        runInEdt {
            FeedbackDialog(DefaultProjectFactory.getInstance().defaultProject).show()
        }
    }
}

class CodeWhispererAuthBullets(private var panelTitle: String) : BorderLayoutPanel() {

    init {
        addToCenter(
            panel {
                indent {

                    row {
                        label(panelTitle).applyToComponent { font = JBFont.h3() }
                    }
                    row {
                        text(Paneltext.COMMIT_ICON)
                        panel {
                            row(message("iam_identity_center.name")) {
                            }
                                .rowComment(message("aws.onboarding.getstarted.panel.idc_row_comment_text"))
                        }
                    }

                    row {
                        text(Paneltext.COMMIT_ICON)
                        panel {
                            row(message("aws_builder_id.service_name")) {
                            }.rowComment("Need to insert tagline")
                        }
                    }
                    row {
                        text(Paneltext.CANCEL_ICON)
                        panel {
                            row(message("settings.credentials.iam")) {
                            }
                                .rowComment(message("aws.getstarted.auth.panel.notSupport_text"))
                        }
                    }
                }
            }
        )

        border = IdeBorderFactory.createRoundedBorder().apply {
            setColor(CodeWhispererColorUtil.POPUP_BUTTON_BORDER)
            preferredSize = Dimension(Paneltext.PANEL_WIDTH, Paneltext.BULLET_PANEL_HEIGHT)
        }
    }
}

class ResourceExplorerAuthBullets(private var panelTitle: String) : BorderLayoutPanel() {

    init {
        addToCenter(
            panel {
                indent {

                    row {
                        label(panelTitle).applyToComponent { font = JBFont.h3() }
                    }

                    row {
                        text(Paneltext.COMMIT_ICON)
                        panel {
                            row(message("iam_identity_center.name")) {
                            }.rowComment(message("aws.onboarding.getstarted.panel.idc_row_comment_text"))
                        }
                    }

                    row {
                        text(Paneltext.CANCEL_ICON)
                        panel {
                            row(message("aws_builder_id.service_name")) {
                            }.rowComment(message("aws.getstarted.auth.panel.notSupport_text"))
                        }
                    }

                    row {
                        text(Paneltext.COMMIT_ICON)
                        panel {
                            row(message("settings.credentials.iam")) {
                            }.rowComment(message("aws.getstarted.auth.panel.notSupport_text"))
                        }
                    }
                }
            }
        )

        border = IdeBorderFactory.createRoundedBorder().apply {
            setColor(CodeWhispererColorUtil.POPUP_BUTTON_BORDER)
            preferredSize = Dimension(Paneltext.PANEL_WIDTH, Paneltext.BULLET_PANEL_HEIGHT)
        }
    }
}

class CodeCatalystAuthBullets(private var panelTitle: String) : BorderLayoutPanel() {

    init {
        addToCenter(
            panel {
                indent {

                    row {
                        label(panelTitle).applyToComponent { font = JBFont.h3() }
                    }

                    row {
                        text(Paneltext.CANCEL_ICON)
                        panel {
                            row(message("iam_identity_center.name")) {
                            }.rowComment(message("aws.getstarted.auth.panel.notSupport_text"))
                        }
                    }

                    row {
                        text(Paneltext.COMMIT_ICON)
                        panel {
                            row(message("aws_builder_id.service_name")) {
                            }.rowComment("Need to insert tagline")
                        }
                    }

                    row {
                        text(Paneltext.CANCEL_ICON)
                        panel {
                            row(message("settings.credentials.iam")) {
                            }.rowComment(message("aws.getstarted.auth.panel.notSupport_text"))
                        }
                    }
                }
            }
        )

        border = IdeBorderFactory.createRoundedBorder().apply {
            setColor(CodeWhispererColorUtil.POPUP_BUTTON_BORDER)
            preferredSize = Dimension(Paneltext.PANEL_WIDTH, Paneltext.BULLET_PANEL_HEIGHT)
        }
    }
}

object Paneltext {
    const val SHARE_FEEDBACK_LINK = "FeedbackDialog"
    const val COMMIT_ICON = "<icon src='AllIcons.General.InspectionsOK'/>&nbsp;"
    const val CANCEL_ICON = "<icon src='AllIcons.CodeWithMe.CwmTerminate'/>&nbsp;"
    val TEXT_FONTCOLOR = JBColor.DARK_GRAY
    val TITLE_TEXT_FONTCOLOR = JBColor(Color.WHITE, Color.WHITE)
    const val PANEL_WIDTH = 300
    const val PANEL_HEIGHT = 350
    const val BULLET_PANEL_HEIGHT = 200
}
