// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.popup

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.idea.AppMode
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_BACKSPACE
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_ENTER
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_ESCAPE
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_TAB
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.RangeMarker
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
import com.intellij.openapi.fileEditor.FileEditorManager
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
import com.intellij.util.messages.Topic
import com.intellij.util.ui.UIUtil
import software.amazon.awssdk.services.codewhispererruntime.model.Import
import software.amazon.awssdk.services.codewhispererruntime.model.Reference
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.codewhisperer.editor.CodeWhispererEditorManager
import software.aws.toolkits.jetbrains.services.codewhisperer.layout.CodeWhispererLayoutConfig.addHorizontalGlue
import software.aws.toolkits.jetbrains.services.codewhisperer.layout.CodeWhispererLayoutConfig.horizontalPanelConstraints
import software.aws.toolkits.jetbrains.services.codewhisperer.layout.CodeWhispererLayoutConfig.inlineLabelConstraints
import software.aws.toolkits.jetbrains.services.codewhisperer.model.DetailContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.PreviewContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContext
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers.CodeWhispererEditorActionHandler
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers.CodeWhispererPopupBackspaceHandler
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers.CodeWhispererPopupEnterHandler
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers.CodeWhispererPopupEscHandler
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers.CodeWhispererPopupLeftArrowHandler
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers.CodeWhispererPopupRightArrowHandler
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers.CodeWhispererPopupTabHandler
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers.CodeWhispererPopupTypedHandler
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.listeners.CodeWhispererAcceptButtonActionListener
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.listeners.CodeWhispererActionListener
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.listeners.CodeWhispererNextButtonActionListener
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.listeners.CodeWhispererPrevButtonActionListener
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.listeners.CodeWhispererScrollListener
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatus
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererService
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
class CodeWhispererPopupManager {
    val popupComponents = CodeWhispererPopupComponents()

    var shouldListenerCancelPopup: Boolean = true
        private set

//    private var myPopup: JBPopup? = null

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
    fun changeStatesForNavigation(sessionContext: SessionContext, indexChange: Int) {
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
        sessionContext: SessionContext,
        typeaheadChange: String,
        typeaheadAdded: Boolean
    ) {
        CodeWhispererService.getInstance().updateTypeahead(typeaheadChange, typeaheadAdded, sessionContext)
        val selectedIndex = findNewSelectedIndex(false, sessionContext.selectedIndex)
        if (selectedIndex == -1) {
            LOG.debug { "None of the recommendation is valid at this point, cancelling the popup" }
            CodeWhispererService.getInstance().disposeDisplaySession(false)
            return
        }

        sessionContext.selectedIndex = selectedIndex
        sessionContext.isFirstTimeShowingPopup = false

        ApplicationManager.getApplication().messageBus.syncPublisher(CODEWHISPERER_POPUP_STATE_CHANGED).stateChanged(
            sessionContext
        )
    }

    @RequiresEdt
    fun changeStatesForShowing(sessionContext: SessionContext, states: InvocationContext, recommendationAdded: Boolean = false) {
        if (recommendationAdded) {
            ApplicationManager.getApplication().messageBus.syncPublisher(CODEWHISPERER_POPUP_STATE_CHANGED)
                .recommendationAdded(states, sessionContext)
            return
        }

        val selectedIndex = findNewSelectedIndex(false, sessionContext.selectedIndex)
        if (selectedIndex == -1) {
            LOG.debug { "None of the recommendation is valid at this point, cancelling the popup" }
            CodeWhispererService.getInstance().disposeDisplaySession(false)
            return
        }

        sessionContext.selectedIndex = selectedIndex
        sessionContext.isFirstTimeShowingPopup = true
        if (sessionContext.popupDisplayOffset == -1) {
            sessionContext.popupDisplayOffset = sessionContext.editor.caretModel.offset
        }

        ApplicationManager.getApplication().messageBus.syncPublisher(CODEWHISPERER_POPUP_STATE_CHANGED).stateChanged(
            sessionContext
        )
    }

    fun updatePopupPanel(sessionContext: SessionContext?) {
        if (sessionContext == null || sessionContext.selectedIndex == -1 || sessionContext.isDisposed()) return
        val selectedIndex = sessionContext.selectedIndex
        val previews = CodeWhispererService.getInstance().getAllSuggestionsPreviewInfo()
        val validCount = getValidCount()
        val validSelectedIndex = getValidSelectedIndex(selectedIndex)
        updateSelectedRecommendationLabelText(validSelectedIndex, validCount)
        updateNavigationPanel(validSelectedIndex, validCount)
        updateImportPanel(previews[selectedIndex].detail.recommendation.mostRelevantMissingImports())
        updateCodeReferencePanel(sessionContext.project, previews[selectedIndex].detail.recommendation.references())
    }

    fun render(sessionContext: SessionContext, isRecommendationAdded: Boolean, isScrolling: Boolean) {
        updatePopupPanel(sessionContext)

        sessionContext.seen.add(sessionContext.selectedIndex)

        // There are four cases that render() is called:
        // 1. Popup showing for the first time, both booleans are false, we should show the popup and update the latency
        // end time, and emit the event if it's at the pagination end.
        // 2. New recommendations being added to the existing ones, we should not update the latency end time, and emit
        // the event if it's at the pagination end.
        // 3. User scrolling (so popup is changing positions), we should not update the latency end time and should not
        // emit any events.
        // 4. User navigating through the completions or typing as the completion shows. We should not update the latency
        // end time and should not emit any events in this case.
        if (!isRecommendationAdded) {
            showPopup(sessionContext)
            if (!isScrolling) {
                sessionContext.latencyContext.codewhispererPostprocessingEnd = System.nanoTime()
                sessionContext.latencyContext.codewhispererEndToEndEnd = System.nanoTime()
            }
        }
        if (isScrolling ||
            CodeWhispererInvocationStatus.getInstance().hasExistingInvocation() ||
            !sessionContext.isFirstTimeShowingPopup
        ) {
            return
        }
//        CodeWhispererTelemetryService.getInstance().sendClientComponentLatencyEvent(sessionContext)
    }

    fun dontClosePopupAndRun(runnable: () -> Unit) {
        try {
            shouldListenerCancelPopup = false
            runnable()
        } finally {
            shouldListenerCancelPopup = true
        }
    }

//    fun resetSession() {
//        sessionContext?.let {
//            Disposer.dispose(it)
//        }
//        sessionContext = null
//    }

    fun showPopup(
        sessionContext: SessionContext,
        force: Boolean = false,
    ) {
        val p = sessionContext.editor.offsetToXY(sessionContext.popupDisplayOffset)
        val popup: JBPopup?
        if (sessionContext.popup == null) {
            popup = initPopup()
            sessionContext.popup = popup
            initPopupListener(sessionContext, popup)
        } else {
            popup = sessionContext.popup
        }
        val editor = sessionContext.editor
        val previews = CodeWhispererService.getInstance().getAllSuggestionsPreviewInfo()
        val userInputOriginal = previews[sessionContext.selectedIndex].userInput
        val userInputLines = userInputOriginal.split("\n").size - 1
        val popupSize = (popup as AbstractPopup).preferredContentSize
        val yAboveFirstLine = p.y - popupSize.height + userInputLines * editor.lineHeight
        val popupRect = Rectangle(p.x, yAboveFirstLine, popupSize.width, popupSize.height)
        val editorRect = editor.scrollingModel.visibleArea
        var shouldHidePopup = false

        CodeWhispererInvocationStatus.getInstance().setDisplaySessionActive(true)

        // Check if the current editor still has focus. If not, don't show the popup.
        val isSameEditorAsTrigger = if (!AppMode.isRemoteDevHost()) {
            editor.contentComponent.isFocusOwner
        } else {
            FileEditorManager.getInstance(sessionContext.project).selectedTextEditorWithRemotes.firstOrNull() == editor
        }
        if (!isSameEditorAsTrigger) {
            LOG.debug { "Current editor no longer has focus, not showing the popup" }
            CodeWhispererService.getInstance().disposeDisplaySession(false)
            return
        }

        if (!editorRect.contains(popupRect)) {
            // popup location above first line don't work, so don't show the popup
            shouldHidePopup = true
        } else {
            LOG.debug {
                "Show popup above the first line of recommendation. " +
                    "Editor position: $editorRect, popup position: $popupRect"
            }
        }

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
//            val perceivedLatency = CodeWhispererInvocationStatus.getInstance().getTimeSinceDocumentChanged()
//            CodeWhispererTelemetryService.getInstance().sendPerceivedLatencyEvent(
//                detail.requestId,
//                states.requestContext,
//                states.responseContext,
//                perceivedLatency
//            )
        }

        val a = ComponentUtil.getWindow(LookupManager.getActiveLookup(editor)?.component)

        if (a != null) {
            val alpha = if (force) 0.8f else 0f
            WindowManager.getInstance().setAlphaModeRatio(a, alpha)
        }

        // popup.popupWindow is null in remote host
        if (!AppMode.isRemoteDevHost()) {
            if (force) {
                WindowManager.getInstance().setAlphaModeRatio(popup.popupWindow, 0.1f)
            } else {
                if (shouldHidePopup) {
                    popup.popupWindow?.let {
                        WindowManager.getInstance().setAlphaModeRatio(it, 1f)
                    }
                }
            }
        }
    }

    fun initPopup(): JBPopup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(popupComponents.panel, null)
        .setAlpha(0.1F)
        .setCancelOnClickOutside(true)
        .setCancelOnOtherWindowOpen(true)
//        .setCancelKeyEnabled(true)
        .setCancelOnWindowDeactivation(true)
        .createPopup()

    fun getReformattedRecommendation(detailContext: DetailContext, userInput: String) =
        detailContext.reformatted.content().substring(userInput.length)

    private fun initPopupListener(sessionContext: SessionContext, popup: JBPopup) {
        addPopupListener(popup)
        sessionContext.editor.scrollingModel.addVisibleAreaListener(CodeWhispererScrollListener(sessionContext), sessionContext)
        addButtonActionListeners(sessionContext)
        addMessageSubscribers(sessionContext)
        setPopupActionHandlers(sessionContext)
        addComponentListeners(sessionContext)
    }

    private fun addPopupListener(popup: JBPopup) {
        val listener = CodeWhispererPopupListener()
        popup.addListener(listener)
        Disposer.register(popup) {
            println("listener is removed")
            popup.removeListener(listener)
        }
    }

    private fun addMessageSubscribers(sessionContext: SessionContext) {
        val connect = ApplicationManager.getApplication().messageBus.connect(sessionContext)
        connect.subscribe(
            CODEWHISPERER_USER_ACTION_PERFORMED,
            object : CodeWhispererUserActionListener {
                override fun navigateNext(sessionContext: SessionContext) {
                    changeStatesForNavigation(sessionContext, 1)
                }

                override fun navigatePrevious(sessionContext: SessionContext) {
                    changeStatesForNavigation(sessionContext, -1)
                }

                override fun backspace(sessionContext: SessionContext, diff: String) {
                    changeStatesForTypeahead(sessionContext, diff, false)
                }

                override fun enter(sessionContext: SessionContext, diff: String) {
                    changeStatesForTypeahead(sessionContext, diff, true)
                }

                override fun type(sessionContext: SessionContext, diff: String) {
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

                override fun beforeAccept(sessionContext: SessionContext) {
                    dontClosePopupAndRun {
                        CodeWhispererEditorManager.getInstance().updateEditorWithRecommendation(sessionContext)
                    }
                    CodeWhispererService.getInstance().disposeDisplaySession(true)
                }
            }
        )
    }

    private fun addButtonActionListeners(sessionContext: SessionContext) {
        popupComponents.prevButton.addButtonActionListener(CodeWhispererPrevButtonActionListener(sessionContext), sessionContext)
        popupComponents.nextButton.addButtonActionListener(CodeWhispererNextButtonActionListener(sessionContext), sessionContext)
        popupComponents.acceptButton.addButtonActionListener(CodeWhispererAcceptButtonActionListener(sessionContext), sessionContext)
    }

    private fun JButton.addButtonActionListener(listener: CodeWhispererActionListener, sessionContext: SessionContext) {
        this.addActionListener(listener)
        Disposer.register(sessionContext) { this.removeActionListener(listener) }
    }

    private fun setPopupActionHandlers(sessionContext: SessionContext) {
        val actionManager = EditorActionManager.getInstance()
        setPopupTypedHandler(CodeWhispererPopupTypedHandler(TypedAction.getInstance().rawHandler, sessionContext), sessionContext)
        setPopupActionHandler(ACTION_EDITOR_TAB, CodeWhispererPopupTabHandler(sessionContext), sessionContext)
        setPopupActionHandler(ACTION_EDITOR_MOVE_CARET_LEFT, CodeWhispererPopupLeftArrowHandler(sessionContext), sessionContext)
        setPopupActionHandler(ACTION_EDITOR_MOVE_CARET_RIGHT, CodeWhispererPopupRightArrowHandler(sessionContext), sessionContext)
        setPopupActionHandler(ACTION_EDITOR_ESCAPE, CodeWhispererPopupEscHandler(sessionContext), sessionContext)
        setPopupActionHandler(
            ACTION_EDITOR_ENTER,
            CodeWhispererPopupEnterHandler(actionManager.getActionHandler(ACTION_EDITOR_ENTER), sessionContext),
            sessionContext
        )
        setPopupActionHandler(
            ACTION_EDITOR_BACKSPACE,
            CodeWhispererPopupBackspaceHandler(actionManager.getActionHandler(ACTION_EDITOR_BACKSPACE), sessionContext),
            sessionContext
        )
    }

    private fun setPopupTypedHandler(newHandler: CodeWhispererPopupTypedHandler, sessionContext: SessionContext) {
        val oldTypedHandler = TypedAction.getInstance().setupRawHandler(newHandler)
        Disposer.register(sessionContext) { TypedAction.getInstance().setupRawHandler(oldTypedHandler) }
    }

    private fun setPopupActionHandler(id: String, newHandler: CodeWhispererEditorActionHandler, sessionContext: SessionContext) {
        val oldHandler = EditorActionManager.getInstance().setActionHandler(id, newHandler)
        Disposer.register(sessionContext) { EditorActionManager.getInstance().setActionHandler(id, oldHandler) }
    }

    private fun addComponentListeners(sessionContext: SessionContext) {
        val editor = sessionContext.editor
        val codeWhispererSelectionListener: SelectionListener = object : SelectionListener {
            override fun selectionChanged(event: SelectionEvent) {
                if (shouldListenerCancelPopup) {
                    CodeWhispererService.getInstance().disposeDisplaySession(false)
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
                        event.newLength > event.oldLength) {
                        dontClosePopupAndRun {
                            super.documentChanged(event)
                            editor.caretModel.moveCaretRelatively(event.newLength, 0, false, false, true)
                            changeStatesForTypeahead(sessionContext, event.newFragment.toString(), true)
                        }
                        return
                    } else {
                        CodeWhispererService.getInstance().disposeDisplaySession(false)
                    }
                }
                super.documentChanged(event)
            }
        }
        editor.document.addDocumentListener(codeWhispererDocumentListener, sessionContext)

        val codeWhispererCaretListener: CaretListener = object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                if (shouldListenerCancelPopup) {
                    CodeWhispererService.getInstance().disposeDisplaySession(false)
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
                override fun componentMoved(event: ComponentEvent) {
                    CodeWhispererService.getInstance().disposeDisplaySession(false)
                }

                override fun componentShown(e: ComponentEvent?) {
                    CodeWhispererService.getInstance().disposeDisplaySession(false)
                    super.componentShown(e)
                }
            }
            window?.addComponentListener(windowListener)
            Disposer.register(sessionContext) { window?.removeComponentListener(windowListener) }
        }


        val suggestionHoverEnterListener: EditorMouseMotionListener = object : EditorMouseMotionListener {
            override fun mouseMoved(e: EditorMouseEvent) {
                e.mouseEvent.component
//                println("current mouse offset : ${e.offset}, point: ${e.mouseEvent.point}")
                val startOffset = editor.offsetToXY(editor.caretModel.offset)
//                println("caret x y: ${startOffset}")
                val point = e.mouseEvent.point
                val right = startOffset.x + (e.inlay?.widthInPixels ?: 0)
                val a = ComponentUtil.getWindow(LookupManager.getActiveLookup(editor)?.component)
                val aPoint =
                if (a != null) {
                    RelativePoint(Point(a.bounds.x, a.bounds.y)).getPoint(editor.contentComponent)
                } else {
                    Point(0, 0)
                }

                if (e.inlay != null) {
                    showPopup(sessionContext, force = true)
                } else {
                    sessionContext.project.messageBus.syncPublisher(
                        CodeWhispererService.CODEWHISPERER_INTELLISENSE_POPUP_ON_HOVER,
                    ).onEnter()
                }
                super.mouseMoved(e)
            }
        }
        editor.addEditorMouseMotionListener(suggestionHoverEnterListener, sessionContext)
    }

    private fun updateSelectedRecommendationLabelText(validSelectedIndex: Int, validCount: Int) {
        if (CodeWhispererInvocationStatus.getInstance().hasExistingInvocation()) {
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
        val previews = CodeWhispererService.getInstance().getAllSuggestionsPreviewInfo()
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
        CodeWhispererService.getInstance().getAllSuggestionsPreviewInfo().filter { isValidRecommendation(it) }.size

    private fun getValidSelectedIndex(selectedIndex: Int): Int {
        var curr = 0

        val previews = CodeWhispererService.getInstance().getAllSuggestionsPreviewInfo()
        previews.forEachIndexed { index, triple ->
            if (index == selectedIndex) {
                return curr
            }
            if (isValidRecommendation(triple)) {
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
        private val LOG = getLogger<CodeWhispererPopupManager>()
        fun getInstance(): CodeWhispererPopupManager = service()
        val CODEWHISPERER_POPUP_STATE_CHANGED: Topic<CodeWhispererPopupStateChangeListener> = Topic.create(
            "CodeWhisperer popup state changed",
            CodeWhispererPopupStateChangeListener::class.java
        )
        val CODEWHISPERER_USER_ACTION_PERFORMED: Topic<CodeWhispererUserActionListener> = Topic.create(
            "CodeWhisperer user action performed",
            CodeWhispererUserActionListener::class.java
        )
    }
}

interface CodeWhispererPopupStateChangeListener {
    fun stateChanged(sessionContext: SessionContext) {}
    fun scrolled(sessionContext: SessionContext) {}
    fun recommendationAdded(states: InvocationContext, sessionContext: SessionContext) {}
}

interface CodeWhispererUserActionListener {
    fun backspace(sessionContext: SessionContext, diff: String) {}
    fun enter(sessionContext: SessionContext, diff: String) {}
    fun type(sessionContext: SessionContext, diff: String) {}
    fun navigatePrevious(sessionContext: SessionContext) {}
    fun navigateNext(sessionContext: SessionContext) {}
    fun beforeAccept(sessionContext: SessionContext) {}
    fun afterAccept(states: InvocationContext, previews: List<PreviewContext>, sessionContext: SessionContext, rangeMarker: RangeMarker) {}
}
