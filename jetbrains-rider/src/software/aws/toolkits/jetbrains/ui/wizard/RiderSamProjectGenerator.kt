// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ui.wizard

import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.xml.util.XmlUtil
import com.jetbrains.rdclient.util.idea.toVirtualFile
import com.jetbrains.rider.ideaInterop.fileTypes.sln.SolutionFileType
import com.jetbrains.rider.projectView.actions.projectTemplating.backend.ReSharperTemplateGeneratorBase
import com.jetbrains.rider.projectView.actions.projectTemplating.impl.ProjectTemplateTransferableModel
import com.jetbrains.rider.projectView.nodes.ProjectModelNode
import com.jetbrains.rider.ui.themes.RiderTheme
import com.jetbrains.rider.util.idea.application
import software.aws.toolkits.resources.message
import java.awt.Dimension
import java.io.File
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.JTextPane

class RiderSamProjectGenerator(
    private val samGenerator: SamProjectGenerator,
    item: ProjectModelNode?,
    group: String,
    categoryName: String,
    model: ProjectTemplateTransferableModel
) : ReSharperTemplateGeneratorBase(
    model = model,
    createSolution = true,
    createProject = true,
    item = item) {

    private val samPanel: SamInitSelectionPanel

    private val tabb: JTabbedPane

    private val structurePane = JTextPane().apply {
        contentType = "text/html"
        isEditable = false
        background = RiderTheme.activeFieldBackground
        border = null
    }

    init {
        title.labels = arrayOf(group, categoryName)

        samPanel = SamInitSelectionPanel(samGenerator.settings)

        tabb = JBTabbedPane()
        val structureScroll = JBScrollPane(structurePane).apply {
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            border = JBUI.Borders.empty()
            background = UIUtil.getTextFieldBackground()
            preferredSize = Dimension(1, JBUI.scale(60))
        }

        tabb.add("Resulting project structure", structureScroll)

        updateInfo()
        super.initialize()
        super.layout()

        addAdditionPane(samPanel.mainPanel)
        addAdditionPane(tabb)
    }

    override fun updateInfo() {
        val sep = File.separator
        val builder = StringBuilder()
        val font = JBUI.Fonts.label()
        builder.appendln("<html><span style=\"font-family:${font.family};font-size:${font.size}\"")

        val solutionDirectory = getSolutionDirectory()
        val projectDirectory = getProjectDirectory()

        val parentName = solutionDirectory?.parentFile?.name
        val parentStr = if (parentName.isNullOrEmpty()) sep else "$sep$parentName$sep"

        val vcsMarker = vcsPanel?.getVcsMarker()
        if (solutionDirectory != null && vcsMarker != null) {
            builder.appendln(htmlText(
                    "$sep${solutionDirectory.parentFile.name}$sep",
                    "${solutionDirectory.name}$sep$vcsMarker"))
        }

        if (solutionDirectory != null) {
            val solutionName = getSolutionName() + SolutionFileType.solutionExtensionWithDot
            builder.appendln(htmlText(parentStr, "${solutionDirectory.name}$sep$solutionName"))
        }

        if (projectDirectory != null) {
            val projectsText = "project files"
            val projectFilesLabel = XmlUtil.escape("<$projectsText>")
            if (solutionDirectory != null && solutionDirectory != projectDirectory) {
                builder.appendln(htmlText(parentStr, "${solutionDirectory.name}$sep${projectDirectory.name}$sep$projectFilesLabel"))
            } else {
                builder.appendln(htmlText(parentStr, "${projectDirectory.name}$sep$projectFilesLabel"))
            }
        }

        builder.appendln("</span></html>")
        structurePane.text = builder.toString()
        super.updateInfo()
    }

    override fun expand() {
        application.invokeLater {
            val selectedRuntime = samGenerator.settings.runtime
            val solutionDirectory = getSolutionDirectory() ?: throw Exception(message("sam.init.error.no.virtual.file"))

            if (!solutionDirectory.exists())
                FileUtil.createDirectory(solutionDirectory)

            val outDirVf = solutionDirectory.toVirtualFile() ?: throw Exception(message("sam.init.error.no.virtual.file"))

            val samTemplate = samGenerator.settings.template
            samTemplate.build(selectedRuntime, outDirVf)
        }
    }

    override fun refreshUI() {
        super.refreshUI()
        validationError.set(null)
    }

    private fun htmlText(s1: String, s2: String) =
        "<font color=#${ColorUtil.toHex(UIUtil.getLabelDisabledForeground())} >...$s1</font>$s2<br>"
}
