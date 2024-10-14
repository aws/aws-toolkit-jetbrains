// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline.listeners

import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import software.aws.toolkits.jetbrains.services.cwc.inline.InlineChatEditorHint
import java.awt.Point

class InlineChatSelectionListener : SelectionListener {
    private var inlineChatEditorHint: InlineChatEditorHint? = null
    override fun selectionChanged(e: SelectionEvent) {
        val editor = e.editor
        val selectionModel = editor.selectionModel

        if (selectionModel.hasSelection()) {
            val selectionEnd = selectionModel.selectionEnd
            val selectionLineEnd = editor.document.getLineEndOffset(editor.document.getLineNumber(selectionEnd))

            val xyPosition = editor.offsetToXY(selectionLineEnd)
            val editorLocation = editor.component.locationOnScreen
            val editorContentLocation = editor.contentComponent.locationOnScreen
            val position = Point(
                editorContentLocation.x + xyPosition.x,
                editorLocation.y + xyPosition.y - editor.scrollingModel.verticalScrollOffset - 50)

            val visibleArea = editor.scrollingModel.visibleArea

            val adjustedX = (position.x ).coerceAtMost(visibleArea.x + visibleArea.width - 50)
            val adjustedY = (position.y ).coerceAtMost(visibleArea.y + visibleArea.height - 50)
            val adjustedPosition = Point(adjustedX, adjustedY)

            inlineChatEditorHint = editor.let { editor.project?.let { project -> InlineChatEditorHint(project, it) } }
            inlineChatEditorHint?.show(adjustedPosition)
        } else {
            inlineChatEditorHint?.hide()
        }
    }
}
