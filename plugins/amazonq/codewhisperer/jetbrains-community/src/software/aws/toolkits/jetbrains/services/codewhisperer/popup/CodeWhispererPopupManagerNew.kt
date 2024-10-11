// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.popup

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.idea.AppMode
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_BACKSPACE
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_ENTER
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_ESCAPE
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.ComponentUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.AbstractPopup
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import software.amazon.awssdk.services.codewhispererruntime.model.Import
import software.amazon.awssdk.services.codewhispererruntime.model.Reference
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.codewhisperer.editor.CodeWhispererEditorManagerNew
import software.aws.toolkits.jetbrains.services.codewhisperer.layout.CodeWhispererLayoutConfig.addHorizontalGlue
import software.aws.toolkits.jetbrains.services.codewhisperer.layout.CodeWhispererLayoutConfig.horizontalPanelConstraints
import software.aws.toolkits.jetbrains.services.codewhisperer.layout.CodeWhispererLayoutConfig.inlineLabelConstraints
import software.aws.toolkits.jetbrains.services.codewhisperer.model.DetailContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.model.PreviewContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererPopupManager.Companion.CODEWHISPERER_POPUP_STATE_CHANGED
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererPopupManager.Companion.CODEWHISPERER_USER_ACTION_PERFORMED
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers.CodeWhispererEditorActionHandlerNew
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers.CodeWhispererPopupBackspaceHandlerNew
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers.CodeWhispererPopupEnterHandlerNew
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers.CodeWhispererPopupEscHandler
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers.CodeWhispererPopupTypedHandlerNew
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.listeners.CodeWhispererAcceptButtonActionListenerNew
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.listeners.CodeWhispererActionListenerNew
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.listeners.CodeWhispererNextButtonActionListenerNew
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.listeners.CodeWhispererPrevButtonActionListenerNew
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.listeners.CodeWhispererScrollListenerNew
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatusNew
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererServiceNew
import software.aws.toolkits.jetbrains.services.codewhisperer.toolwindow.CodeWhispererCodeReferenceManager
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererColorUtil.POPUP_DIM_HEX
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.POPUP_INFO_TEXT_SIZE
import software.aws.toolkits.resources.message
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel

@Service
class CodeWhispererPopupManagerNew {
    val popupComponents = CodeWhispererPopupComponentsNew()

    var shouldListenerCancelPopup: Boolean = true
        private set

    init {
        // Listen for global scheme changes
        ApplicationManager.getApplication().messageBus.connect().subscribe(
            EditorColorsManager.TOPIC,
            EditorColorsListener { scheme ->
                if (scheme == null) return@EditorColorsListener
                popupComponents.apply {
                    panel.background = scheme.defaultBackground
                    panel.components.forEach {
                        it.background = scheme.getColor(EditorColors.DOCUMENTATION_COLOR)
                        it.foreground = scheme.defaultForeground
                    }
                    buttonsPanel.components.forEach {
                        it.foreground = UIUtil.getLabelForeground()
                    }
                    recommendationInfoLabel.foreground = UIUtil.getLabelForeground()
                    codeReferencePanel.components.forEach {
                        it.background = scheme.getColor(EditorColors.DOCUMENTATION_COLOR)
                        it.foreground = UIUtil.getLabelForeground()
                    }
                }
            }
        )
    }

    @RequiresEdt
    fun changeStatesForNavigation(sessionContext: SessionContextNew, indexChange: Int) {
        val validCount = getValidCount()
        val validSelectedIndex = getValidSelectedIndex(sessionContext.selectedIndex)
        if ((validSelectedIndex == validCount - 1 && indexChange == 1) ||
            (validSelectedIndex == 0 && indexChange == -1)
        ) {
            return
        }
        val isReverse = indexChange < 0
        val selectedIndex = findNewSelectedIndex(isReverse, sessionContext.selectedIndex + indexChange)

        sessionContext.selectedIndex = selectedIndex
        sessionContext.isFirstTimeShowingPopup = false

        ApplicationManager.getApplication().messageBus.syncPublisher(CODEWHISPERER_POPUP_STATE_CHANGED).stateChanged(
            sessionContext
        )
    }

    @RequiresEdt
    fun changeStatesForTypeahead(
        sessionContext: SessionContextNew,
        typeaheadChange: String,
        typeaheadAdded: Boolean,
    ) {
        if (!updateTypeahead(typeaheadChange, typeaheadAdded)) return
        if (!updateSessionSelectedIndex(sessionContext)) return
        sessionContext.isFirstTimeShowingPopup = false

        ApplicationManager.getApplication().messageBus.syncPublisher(CODEWHISPERER_POPUP_STATE_CHANGED).stateChanged(
            sessionContext
        )
    }

    @RequiresEdt
    fun changeStatesForShowing(sessionContext: SessionContextNew, states: InvocationContextNew, recommendationAdded: Boolean = false) {
        sessionContext.isFirstTimeShowingPopup = !recommendationAdded
        if (recommendationAdded) {
            ApplicationManager.getApplication().messageBus.syncPublisher(CODEWHISPERER_POPUP_STATE_CHANGED)
                .recommendationAdded(states, sessionContext)
            return
        }

        if (!updateSessionSelectedIndex(sessionContext)) return
        if (sessionContext.popupOffset == -1) {
            sessionContext.popupOffset = sessionContext.editor.caretModel.offset
        }

        ApplicationManager.getApplication().messageBus.syncPublisher(CODEWHISPERER_POPUP_STATE_CHANGED).stateChanged(
            sessionContext
        )
    }

    private fun updateTypeahead(typeaheadChange: String, typeaheadAdded: Boolean): Boolean {
        val recommendations = CodeWhispererServiceNew.getInstance().getAllPaginationSessions().values.filterNotNull()
        recommendations.forEach {
            val newTypeahead =
                if (typeaheadAdded) {
                    it.recommendationContext.typeahead + typeaheadChange
                } else {
                    if (typeaheadChange.length > it.recommendationContext.typeahead.length) {
                        LOG.debug { "Typeahead change is longer than the current typeahead, exiting the session" }
                        CodeWhispererServiceNew.getInstance().disposeDisplaySession(false)
                        return false
                    }
                    it.recommendationContext.typeahead.substring(
                        0,
                        it.recommendationContext.typeahead.length - typeaheadChange.length
                    )
                }
            it.recommendationContext.typeahead = newTypeahead
        }
        return true
    }

    private fun updateSessionSelectedIndex(sessionContext: SessionContextNew): Boolean {
        val selectedIndex = findNewSelectedIndex(false, sessionContext.selectedIndex)
        if (selectedIndex == -1) {
            LOG.debug { "None of the recommendation is valid at this point, cancelling the popup" }
            CodeWhispererServiceNew.getInstance().disposeDisplaySession(false)
            return false
        }

        sessionContext.selectedIndex = selectedIndex
        return true
    }

    fun updatePopupPanel(sessionContext: SessionContextNew?) {
        if (sessionContext == null || sessionContext.selectedIndex == -1 || sessionContext.isDisposed()) return
        val selectedIndex = sessionContext.selectedIndex
        val previews = CodeWhispererServiceNew.getInstance().getAllSuggestionsPreviewInfo()
        if (selectedIndex >= previews.size) return
        val validCount = getValidCount()
        val validSelectedIndex = getValidSelectedIndex(selectedIndex)
        updateSelectedRecommendationLabelText(validSelectedIndex, validCount)
        updateNavigationPanel(validSelectedIndex, validCount)
        updateImportPanel(previews[selectedIndex].detail.recommendation.mostRelevantMissingImports())
        updateCodeReferencePanel(sessionContext.project, previews[selectedIndex].detail.recommendation.references())
    }

    fun render(sessionContext: SessionContextNew, isRecommendationAdded: Boolean, isScrolling: Boolean) {
        updatePopupPanel(sessionContext)

        // There are four cases that render() is called:
        // 1. Popup showing for the first time, both booleans are false, we should show the popup and update the latency
        // end time, and emit the event if it's at the pagination end.
        // 2. New recommendations being added to the existing ones, we should not update the latency end time, and emit
        // the event if it's at the pagination end.
        // 3. User scrolling (so popup is changing positions), we should not update the latency end time and should not
        // emit any events.
        // 4. User navigating through the completions or typing as the completion shows. We should not update the latency
        // end time and should not emit any events in this case.
        if (isRecommendationAdded) return
        showPopup(sessionContext)
        if (isScrolling) return
        sessionContext.latencyContext.codewhispererPostprocessingEnd = System.nanoTime()
        sessionContext.latencyContext.codewhispererEndToEndEnd = System.nanoTime()
    }

    fun dontClosePopupAndRun(runnable: () -> Unit) {
        try {
            shouldListenerCancelPopup = false
            runnable()
        } finally {
            shouldListenerCancelPopup = true
        }
    }

    fun showPopup(sessionContext: SessionContextNew, force: Boolean = false) {
        val p = sessionContext.editor.offsetToXY(sessionContext.popupOffset)
        val popup: JBPopup?
        if (sessionContext.popup == null) {
            popup = initPopup()
            sessionContext.popup = popup
            CodeWhispererInvocationStatusNew.getInstance().setPopupStartTimestamp()
            initPopupListener(sessionContext, popup)
        } else {
            popup = sessionContext.popup
        }
        val editor = sessionContext.editor
        val previews = CodeWhispererServiceNew.getInstance().getAllSuggestionsPreviewInfo()
        val userInputOriginal = previews[sessionContext.selectedIndex].userInput
        val userInputLines = userInputOriginal.split("\n").size - 1
        val popupSize = (popup as AbstractPopup).preferredContentSize
        val yAboveFirstLine = p.y - popupSize.height + userInputLines * editor.lineHeight
        val popupRect = Rectangle(p.x, yAboveFirstLine, popupSize.width, popupSize.height)
        val editorRect = editor.scrollingModel.visibleArea
        var shouldHidePopup = false

        CodeWhispererInvocationStatusNew.getInstance().setDisplaySessionActive(true)

        if (!editorRect.contains(popupRect)) {
            // popup location above first line don't work, so don't show the popup
            shouldHidePopup = true
        }

        // popup to always display above the current editing line
        val popupLocation = Point(p.x, yAboveFirstLine)

        val relativePopupLocationToEditor = RelativePoint(editor.contentComponent, popupLocation)

        // TODO: visibleAreaChanged listener is not getting triggered in remote environment when scrolling
        if (popup.isVisible) {
            // Changing the position of BackendBeAbstractPopup does not work
            if (!shouldHidePopup && !AppMode.isRemoteDevHost()) {
                popup.setLocation(relativePopupLocationToEditor.screenPoint)
                popup.size = popup.preferredContentSize
            }
        } else {
            if (!AppMode.isRemoteDevHost()) {
                if (force && !shouldHidePopup) {
                    popup.show(relativePopupLocationToEditor)
                }
            } else {
                // TODO: Fix in remote case the popup should display above the current editing line
                // TODO: For now, the popup will always display below the suggestions, without checking
                // if the location the popup is about to show at stays in the editor window or not, due to
                // the limitation of BackendBeAbstractPopup
                val caretVisualPosition = editor.offsetToVisualPosition(editor.caretModel.offset)

                // display popup x lines below the caret where x is # of lines of suggestions, since inlays don't
                // count as visual lines, the final math will always be just increment 1 line.
                val popupPositionForRemote = VisualPosition(
                    caretVisualPosition.line + 1,
                    caretVisualPosition.column
                )
                editor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POSITION, popupPositionForRemote)
                popup.showInBestPositionFor(editor)
            }
        }

        bringSuggestionInlayToFront(editor, popup, !force)
    }

    fun bringSuggestionInlayToFront(editor: Editor, popup: JBPopup?, opposite: Boolean = false) {
        val qInlinePopupAlpha = if (opposite) 1f else 0.1f
        val intelliSensePopupAlpha = if (opposite) 0f else 0.8f

        (popup as AbstractPopup?)?.popupWindow?.let {
            WindowManager.getInstance().setAlphaModeRatio(it, qInlinePopupAlpha)
        }
        ComponentUtil.getWindow(LookupManager.getActiveLookup(editor)?.component)?.let {
            WindowManager.getInstance().setAlphaModeRatio(it, intelliSensePopupAlpha)
        }
    }

    fun initPopup(): JBPopup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(popupComponents.panel, null)
        .setAlpha(0.1F)
        .setCancelOnClickOutside(true)
        .setCancelOnWindowDeactivation(true)
        .createPopup()

    fun getReformattedRecommendation(detailContext: DetailContextNew, userInput: String) =
        detailContext.reformatted.content().substring(userInput.length)

    private fun initPopupListener(sessionContext: SessionContextNew, popup: JBPopup) {
        addPopupListener(popup)
        sessionContext.editor.scrollingModel.addVisibleAreaListener(CodeWhispererScrollListenerNew(sessionContext), sessionContext)
        addButtonActionListeners(sessionContext)
        addMessageSubscribers(sessionContext)
        setPopupActionHandlers(sessionContext)
        addComponentListeners(sessionContext)
    }

    private fun addPopupListener(popup: JBPopup) {
        val listener = CodeWhispererPopupListenerNew()
        popup.addListener(listener)
        Disposer.register(popup) {
            popup.removeListener(listener)
        }
    }

    private fun addMessageSubscribers(sessionContext: SessionContextNew) {
        val connect = ApplicationManager.getApplication().messageBus.connect(sessionContext)
        connect.subscribe(
            CODEWHISPERER_USER_ACTION_PERFORMED,
            object : CodeWhispererUserActionListener {
                override fun navigateNext(sessionContext: SessionContextNew) {
                    changeStatesForNavigation(sessionContext, 1)
                }

                override fun navigatePrevious(sessionContext: SessionContextNew) {
                    changeStatesForNavigation(sessionContext, -1)
                }

                override fun backspace(sessionContext: SessionContextNew, diff: String) {
                    changeStatesForTypeahead(sessionContext, diff, false)
                }

                override fun enter(sessionContext: SessionContextNew, diff: String) {
                    changeStatesForTypeahead(sessionContext, diff, true)
                }

                override fun type(sessionContext: SessionContextNew, diff: String) {
                    // remove the character at primaryCaret if it's the same as the typed character
                    val caretOffset = sessionContext.editor.caretModel.primaryCaret.offset
                    val document = sessionContext.editor.document
                    val text = document.charsSequence
                    if (caretOffset < text.length && diff == text[caretOffset].toString()) {
                        WriteCommandAction.runWriteCommandAction(sessionContext.project) {
                            document.deleteString(caretOffset, caretOffset + 1)
                        }
                    }
                    changeStatesForTypeahead(sessionContext, diff, true)
                }

                override fun beforeAccept(sessionContext: SessionContextNew) {
                    dontClosePopupAndRun {
                        CodeWhispererEditorManagerNew.getInstance().updateEditorWithRecommendation(sessionContext)
                    }
                    CodeWhispererServiceNew.getInstance().disposeDisplaySession(true)
                }
            }
        )
    }

    private fun addButtonActionListeners(sessionContext: SessionContextNew) {
        popupComponents.prevButton.addButtonActionListener(CodeWhispererPrevButtonActionListenerNew(sessionContext), sessionContext)
        popupComponents.nextButton.addButtonActionListener(CodeWhispererNextButtonActionListenerNew(sessionContext), sessionContext)
        popupComponents.acceptButton.addButtonActionListener(CodeWhispererAcceptButtonActionListenerNew(sessionContext), sessionContext)
    }

    private fun JButton.addButtonActionListener(listener: CodeWhispererActionListenerNew, sessionContext: SessionContextNew) {
        this.addActionListener(listener)
        Disposer.register(sessionContext) { this.removeActionListener(listener) }
    }

    private fun setPopupActionHandlers(sessionContext: SessionContextNew) {
        val actionManager = EditorActionManager.getInstance()

        sessionContext.project.putUserData(CodeWhispererServiceNew.KEY_SESSION_CONTEXT, sessionContext)

        setPopupTypedHandler(CodeWhispererPopupTypedHandlerNew(TypedAction.getInstance().rawHandler, sessionContext), sessionContext)
        setPopupActionHandler(ACTION_EDITOR_ESCAPE, CodeWhispererPopupEscHandler(sessionContext), sessionContext)
        setPopupActionHandler(
            ACTION_EDITOR_ENTER,
            CodeWhispererPopupEnterHandlerNew(actionManager.getActionHandler(ACTION_EDITOR_ENTER), sessionContext),
            sessionContext
        )
        setPopupActionHandler(
            ACTION_EDITOR_BACKSPACE,
            CodeWhispererPopupBackspaceHandlerNew(actionManager.getActionHandler(ACTION_EDITOR_BACKSPACE), sessionContext),
            sessionContext
        )
    }

    private fun setPopupTypedHandler(newHandler: CodeWhispererPopupTypedHandlerNew, sessionContext: SessionContextNew) {
        val oldTypedHandler = TypedAction.getInstance().setupRawHandler(newHandler)
        Disposer.register(sessionContext) { TypedAction.getInstance().setupRawHandler(oldTypedHandler) }
    }

    private fun setPopupActionHandler(id: String, newHandler: CodeWhispererEditorActionHandlerNew, sessionContext: SessionContextNew) {
        val oldHandler = EditorActionManager.getInstance().setActionHandler(id, newHandler)
        Disposer.register(sessionContext) { EditorActionManager.getInstance().setActionHandler(id, oldHandler) }
    }

    private fun addComponentListeners(sessionContext: SessionContextNew) {
        val editor = sessionContext.editor
        val codeWhispererSelectionListener: SelectionListener = object : SelectionListener {
            override fun selectionChanged(event: SelectionEvent) {
                if (shouldListenerCancelPopup) {
                    CodeWhispererServiceNew.getInstance().disposeDisplaySession(false)
                }
                super.selectionChanged(event)
            }
        }
        editor.selectionModel.addSelectionListener(codeWhispererSelectionListener)
        Disposer.register(sessionContext) { editor.selectionModel.removeSelectionListener(codeWhispererSelectionListener) }

        val codeWhispererDocumentListener: DocumentListener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (shouldListenerCancelPopup) {
                    // handle IntelliSense accept case
                    // TODO: handle bulk delete (delete word) case
                    if (editor.document == event.document &&
                        editor.caretModel.offset == event.offset &&
                        event.newLength > event.oldLength
                    ) {
                        dontClosePopupAndRun {
                            super.documentChanged(event)
                            editor.caretModel.moveCaretRelatively(event.newLength, 0, false, false, true)
                            changeStatesForTypeahead(sessionContext, event.newFragment.toString(), true)
                        }
                        return
                    } else {
                        CodeWhispererServiceNew.getInstance().disposeDisplaySession(false)
                    }
                }
                super.documentChanged(event)
            }
        }
        editor.document.addDocumentListener(codeWhispererDocumentListener, sessionContext)

        val codeWhispererCaretListener: CaretListener = object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                if (shouldListenerCancelPopup) {
                    CodeWhispererServiceNew.getInstance().disposeDisplaySession(false)
                }
                super.caretPositionChanged(event)
            }
        }
        editor.caretModel.addCaretListener(codeWhispererCaretListener)
        Disposer.register(sessionContext) { editor.caretModel.removeCaretListener(codeWhispererCaretListener) }

        val editorComponent = editor.contentComponent
        if (editorComponent.isShowing) {
            val window = ComponentUtil.getWindow(editorComponent)
            val windowListener: ComponentListener = object : ComponentAdapter() {
                override fun componentMoved(e: ComponentEvent) {
                    CodeWhispererServiceNew.getInstance().disposeDisplaySession(false)
                    super.componentMoved(e)
                }

                override fun componentShown(e: ComponentEvent?) {
                    CodeWhispererServiceNew.getInstance().disposeDisplaySession(false)
                    super.componentShown(e)
                }
            }
            window?.addComponentListener(windowListener)
            Disposer.register(sessionContext) { window?.removeComponentListener(windowListener) }
        }

        val suggestionHoverEnterListener: EditorMouseMotionListener = object : EditorMouseMotionListener {
            override fun mouseMoved(e: EditorMouseEvent) {
                if (e.inlay != null) {
                    showPopup(sessionContext, force = true)
                } else {
                    bringSuggestionInlayToFront(sessionContext.editor, sessionContext.popup, opposite = true)
                }
                super.mouseMoved(e)
            }
        }
        editor.addEditorMouseMotionListener(suggestionHoverEnterListener, sessionContext)
    }

    private fun updateSelectedRecommendationLabelText(validSelectedIndex: Int, validCount: Int) {
        if (CodeWhispererInvocationStatusNew.getInstance().hasExistingServiceInvocation()) {
            popupComponents.recommendationInfoLabel.text = message("codewhisperer.popup.pagination_info")
            LOG.debug { "Pagination in progress. Current total: $validCount" }
        } else {
            popupComponents.recommendationInfoLabel.text =
                message(
                    "codewhisperer.popup.recommendation_info",
                    validSelectedIndex + 1,
                    validCount,
                    POPUP_DIM_HEX
                )
            LOG.debug { "Updated popup recommendation label text. Index: $validSelectedIndex, total: $validCount" }
        }
    }

    private fun updateNavigationPanel(validSelectedIndex: Int, validCount: Int) {
        val multipleRecommendation = validCount > 1
        popupComponents.prevButton.isEnabled = multipleRecommendation && validSelectedIndex != 0
        popupComponents.nextButton.isEnabled = multipleRecommendation && validSelectedIndex != validCount - 1
    }

    private fun updateImportPanel(imports: List<Import>) {
        popupComponents.panel.apply {
            if (components.contains(popupComponents.importPanel)) {
                remove(popupComponents.importPanel)
            }
        }
        if (imports.isEmpty()) return

        val firstImport = imports.first()
        val choice = if (imports.size > 2) 2 else imports.size - 1
        val message = message("codewhisperer.popup.import_info", firstImport.statement(), imports.size - 1, choice)
        popupComponents.panel.add(popupComponents.importPanel, horizontalPanelConstraints)
        popupComponents.importLabel.text = message
    }

    private fun updateCodeReferencePanel(project: Project, references: List<Reference>) {
        popupComponents.panel.apply {
            if (components.contains(popupComponents.codeReferencePanel)) {
                remove(popupComponents.codeReferencePanel)
            }
        }
        if (references.isEmpty()) return

        popupComponents.panel.add(popupComponents.codeReferencePanel, horizontalPanelConstraints)
        val licenses = references.map { it.licenseName() }.toSet()
        popupComponents.codeReferencePanelLink.apply {
            actionListeners.toList().forEach {
                removeActionListener(it)
            }
            addActionListener {
                CodeWhispererCodeReferenceManager.getInstance(project).showCodeReferencePanel()
            }
        }
        popupComponents.licenseCodePanel.apply {
            removeAll()
            add(popupComponents.licenseCodeLabelPrefixText, inlineLabelConstraints)
            licenses.forEachIndexed { i, license ->
                add(popupComponents.licenseLink(license), inlineLabelConstraints)
                if (i == licenses.size - 1) return@forEachIndexed
                add(JLabel(", "), inlineLabelConstraints)
            }

            add(JLabel(".  "), inlineLabelConstraints)
            add(popupComponents.codeReferencePanelLink, inlineLabelConstraints)
            addHorizontalGlue()
        }
        popupComponents.licenseCodePanel.components.forEach {
            if (it !is JComponent) return@forEach
            it.font = it.font.deriveFont(POPUP_INFO_TEXT_SIZE)
        }
    }

    fun findNewSelectedIndex(isReverse: Boolean, selectedIndex: Int): Int {
        val start = if (selectedIndex == -1) 0 else selectedIndex
        val previews = CodeWhispererServiceNew.getInstance().getAllSuggestionsPreviewInfo()
        val count = previews.size
        val unit = if (isReverse) -1 else 1
        var currIndex: Int
        for (i in 0 until count) {
            currIndex = (start + i * unit) % count
            if (currIndex < 0) {
                currIndex += count
            }
            if (isValidRecommendation(previews[currIndex])) {
                return currIndex
            }
        }
        return -1
    }

    private fun getValidCount(): Int =
        CodeWhispererServiceNew.getInstance().getAllSuggestionsPreviewInfo().filter { isValidRecommendation(it) }.size

    private fun getValidSelectedIndex(selectedIndex: Int): Int {
        var curr = 0

        val previews = CodeWhispererServiceNew.getInstance().getAllSuggestionsPreviewInfo()
        previews.forEachIndexed { index, preview ->
            if (index == selectedIndex) {
                return curr
            }
            if (isValidRecommendation(preview)) {
                curr++
            }
        }
        return -1
    }

    private fun isValidRecommendation(preview: PreviewContext): Boolean {
        if (preview.detail.isDiscarded) return false
        return preview.detail.recommendation.content().startsWith(preview.userInput + preview.typeahead)
    }

    companion object {
        private val LOG = getLogger<CodeWhispererPopupManagerNew>()
        fun getInstance(): CodeWhispererPopupManagerNew = service()
    }
}
