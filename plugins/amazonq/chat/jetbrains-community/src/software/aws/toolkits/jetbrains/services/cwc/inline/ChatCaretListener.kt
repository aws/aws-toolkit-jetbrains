// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import icons.AwsIcons
import software.aws.toolkits.jetbrains.services.amazonq.apps.AmazonQAppInitContext
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.SwingUtilities

class ChatCaretListener(private val project: Project, private val context: AmazonQAppInitContext) : CaretListener {
    private var currentHighlighter: RangeHighlighter? = null
    init {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        editor?.caretModel?.addCaretListener(this)
    }

    override fun caretPositionChanged(event: CaretEvent) {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val lineNumber = event.newPosition.line
        val startOffset = editor.document.getLineStartOffset(lineNumber)
        val endOffset = editor.document.getLineEndOffset(lineNumber)
        val markupModel: MarkupModel = editor.markupModel
        val gutterIconRenderer = ChatGutterIconRenderer(AwsIcons.Logos.AWS_Q_GREY).apply {
            setClickAction {
                val action = OpenChatInputAction()
                val dataContext = DataManager.getInstance().getDataContext(editor.component)
                val e = AnActionEvent.createFromDataContext(
                    "GutterIconClick",
                    Presentation(),
                    dataContext
                )
                action.actionPerformed(e)
            }
        }

        if (event.oldPosition.line != event.newPosition.line) {
            currentHighlighter?.let {
                editor.markupModel.removeHighlighter(it)
            }
            markupModel.apply {
                val highlighter = addRangeHighlighter(
                    startOffset,
                    endOffset,
                    HighlighterLayer.CARET_ROW,
                    null,
                    HighlighterTargetArea.LINES_IN_RANGE
                )
                currentHighlighter = highlighter
                highlighter.gutterIconRenderer = gutterIconRenderer
            }
        }
    }
}

private class ChatGutterIconRenderer(private val icon: Icon) : GutterIconRenderer() {
    private var clickAction: (() -> Unit)? = null
    override fun equals(other: Any?): Boolean {
        if (other is ChatGutterIconRenderer) {
            return icon == other.icon
        }
        return false
    }

    override fun hashCode(): Int = icon.hashCode()

    override fun getIcon(): Icon = icon

    override fun getTooltipText(): String = "Ask Amazon Q"

    override fun isNavigateAction(): Boolean = false

    override fun getClickAction(): AnAction = object : AnAction() {
        // bring up the chat inputbox
        override fun actionPerformed(e: AnActionEvent) = clickAction?.invoke() ?: Unit
        override fun update(e: AnActionEvent) = Unit
    }

    fun setClickAction (action: () -> Unit) {
        clickAction = action
    }

    override fun getPopupMenuActions(): ActionGroup? = null

    override fun getAlignment(): Alignment = Alignment.LEFT
}

