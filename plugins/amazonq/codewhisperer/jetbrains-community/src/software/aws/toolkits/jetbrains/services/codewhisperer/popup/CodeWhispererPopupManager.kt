// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.popup

import com.intellij.codeInsight.hint.ParameterInfoController
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.idea.AppMode
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_BACKSPACE
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_ENTER
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_TAB
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
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
import com.jetbrains.rd.swing.awtMousePoint
import groovy.lang.Tuple3
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
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContext
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers.CodeWhispererEditorActionHandler
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers.CodeWhispererPopupBackspaceHandler
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers.CodeWhispererPopupEnterHandler
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
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.CodeWhispererTelemetryService
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
    var sessionContext: SessionContext? = null

    private var myPopup: JBPopup? = null

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
    fun changeStatesForNavigation(states: InvocationContext, indexChange: Int) {
        val details = CodeWhispererService.getInstance().ongoingRequests.values.filterNotNull().flatMap { element ->
            val context = element.recommendationContext
            context.details.map {
                Triple(it, context.userInputSinceInvocation, context.typeaheadOriginal)
            }
        }
        var sessionContext = sessionContext ?: SessionContext()
        this.sessionContext = sessionContext

        val isReverse = indexChange < 0
        val userInput = states.recommendationContext.userInputSinceInvocation
        val validCount = getValidCount(emptyList(), userInput, "")
        val validSelectedIndex = getValidSelectedIndex(emptyList(), userInput, sessionContext.selectedIndex, "")
        if ((validSelectedIndex == validCount - 1 && indexChange == 1) ||
            (validSelectedIndex == 0 && indexChange == -1)
        ) {
            return
        }
        val selectedIndex = findNewSelectedIndex(
            isReverse,
            sessionContext.selectedIndex + indexChange
        )
        if (selectedIndex == -1 || !isValidRecommendation(details[selectedIndex].first, details[selectedIndex].second, details[selectedIndex].third)) {
            LOG.debug { "None of the recommendation is valid at this point, cancelling the popup" }
            Disposer.dispose(sessionContext)
            return
        }

        sessionContext.selectedIndex = selectedIndex
        sessionContext.isFirstTimeShowingPopup = false

        ApplicationManager.getApplication().messageBus.syncPublisher(CODEWHISPERER_POPUP_STATE_CHANGED).stateChanged(
            states,
            sessionContext
        )
    }

    @RequiresEdt
    fun changeStatesForTypeahead(
        states: InvocationContext,
        typeaheadChange: String,
        typeaheadAdded: Boolean
    ) {
        var sessionContext = sessionContext ?: SessionContext()
        this.sessionContext = sessionContext
        val recos = CodeWhispererService.getInstance().ongoingRequests.values.filterNotNull()
        val userInput = states.recommendationContext.userInputSinceInvocation

        sessionContext.selectedIndex
        recos.forEach {
            val typeaheadOriginal =
                if (typeaheadAdded) {
                    it.recommendationContext.typeaheadOriginal + typeaheadChange
                } else {
                    if (typeaheadChange.length > it.recommendationContext.typeaheadOriginal.length) {
                        Disposer.dispose(sessionContext)
                        println("exit 7, ")
                        return
                    }
                    it.recommendationContext.typeaheadOriginal.substring(
                        0,
                        it.recommendationContext.typeaheadOriginal.length - typeaheadChange.length
                    )
                }
            it.recommendationContext.typeaheadOriginal = typeaheadOriginal
        }
        val isReverse = false
        val selectedIndex = findNewSelectedIndex(
            isReverse,
            sessionContext.selectedIndex
        )
        val details = CodeWhispererService.getInstance().ongoingRequests.values.filterNotNull().flatMap { element ->
            element.recommendationContext.details.map {
                Triple(it, element.recommendationContext.userInputSinceInvocation, element.recommendationContext.typeaheadOriginal)
            }
        }
        if (selectedIndex == -1 || !isValidRecommendation(details[selectedIndex].first, details[selectedIndex].second, details[selectedIndex].third)) {
            LOG.debug { "None of the recommendation is valid at this point, cancelling the popup" }
            Disposer.dispose(sessionContext)
//            cancelPopup()
            return
        }

//        recos.forEach {
//            it.recommendationContext.typeahead = resolveTypeahead(it, details, selectedIndex, it.recommendationContext.typeaheadOriginal)
//        }
//        sessionContext.typeahead = resolveTypeahead(states, selectedIndex, typeaheadOriginal)
//        sessionContext.typeaheadOriginal = typeaheadOriginal
        sessionContext.selectedIndex = selectedIndex
        sessionContext.isFirstTimeShowingPopup = false

        ApplicationManager.getApplication().messageBus.syncPublisher(CODEWHISPERER_POPUP_STATE_CHANGED).stateChanged(
            states,
            sessionContext
        )
    }

    @RequiresEdt
    fun changeStatesForShowing(
        states: InvocationContext,
        recommendationAdded: Boolean = false
    ) {
        var sessionContext = sessionContext ?: SessionContext()
        this.sessionContext = sessionContext
        if (recommendationAdded) {
//            LOG.debug {
//                "Add recommendations to the existing CodeWhisperer session, current number of recommendations: ${details.size}"
//            }
            ApplicationManager.getApplication().messageBus.syncPublisher(CODEWHISPERER_POPUP_STATE_CHANGED)
                .recommendationAdded(states, sessionContext)
            return
        }
        val isReverse = false

        val selectedIndex = findNewSelectedIndex(
            isReverse,
            sessionContext.selectedIndex,
        )
        val details = CodeWhispererService.getInstance().ongoingRequests.values.filterNotNull().flatMap { element ->
            element.recommendationContext.details.map {
                Triple(it, element.recommendationContext.userInputSinceInvocation, element.recommendationContext.typeaheadOriginal)
            }
        }
        if (selectedIndex == -1 || !isValidRecommendation(details[selectedIndex].first, details[selectedIndex].second, details[selectedIndex].third)) {
            LOG.debug { "None of the recommendation is valid at this point, cancelling the popup" }
            Disposer.dispose(sessionContext)
//            cancelPopup()
            return
        }

        sessionContext.selectedIndex = selectedIndex
        sessionContext.isFirstTimeShowingPopup = true
        if (sessionContext.popupDisplayOffset == -1) {
            sessionContext.popupDisplayOffset = states.requestContext.editor.caretModel.offset
        }

        ApplicationManager.getApplication().messageBus.syncPublisher(CODEWHISPERER_POPUP_STATE_CHANGED).stateChanged(
            states,
            sessionContext
        )
    }

//    fun changeStates(
//        states: InvocationContext,
//        indexChange: Int,
//        typeaheadChange: String,
//        typeaheadAdded: Boolean,
//        recommendationAdded: Boolean = false
//    ) {
//        val (_, _, recommendationContext) = states
//        val (details) = recommendationContext
//        var sessionContext = sessionContext ?: SessionContext()
//        if (recommendationAdded) {
//            LOG.debug {
//                "Add recommendations to the existing CodeWhisperer session, current number of recommendations: ${details.size}"
//            }
//            ApplicationManager.getApplication().messageBus.syncPublisher(CODEWHISPERER_POPUP_STATE_CHANGED)
//                .recommendationAdded(states, sessionContext)
//            return
//        }
//        val typeaheadOriginal =
//            if (typeaheadAdded) {
//                recommendationContext.typeaheadOriginal + typeaheadChange
//            } else {
//                if (typeaheadChange.length > recommendationContext.typeaheadOriginal.length) {
//                    Disposer.dispose(sessionContext)
//                    println("exit 7, ")
//                    return
//                }
//                sessionContext.typeaheadOriginal.substring(
//                    0,
//                    sessionContext.typeaheadOriginal.length - typeaheadChange.length
//                )
//            }
//        val isReverse = indexChange < 0
//        val userInput = states.recommendationContext.userInputSinceInvocation
//        val validCount = getValidCount(details, userInput, typeaheadOriginal)
//        val validSelectedIndex = getValidSelectedIndex(details, userInput, sessionContext.selectedIndex, typeaheadOriginal)
//        if ((validSelectedIndex == validCount - 1 && indexChange == 1) ||
//            (validSelectedIndex == 0 && indexChange == -1)
//        ) {
//            return
//        }
//        val selectedIndex = findNewSelectedIndex(
//            isReverse,
//            sessionContext.selectedIndex + indexChange
//        )
//        if (selectedIndex == -1 || !isValidRecommendation(details[selectedIndex], userInput, typeaheadOriginal)) {
//            LOG.debug { "None of the recommendation is valid at this point, cancelling the popup" }
//            Disposer.dispose(sessionContext)
//            return
//        }
//
//        sessionContext.typeahead = resolveTypeahead(states, selectedIndex, typeaheadOriginal)
//        sessionContext.typeaheadOriginal = typeaheadOriginal
//        sessionContext.selectedIndex = selectedIndex
//        sessionContext.isFirstTimeShowingPopup = indexChange == 0 && typeaheadChange.isEmpty()
//
//        ApplicationManager.getApplication().messageBus.syncPublisher(CODEWHISPERER_POPUP_STATE_CHANGED).stateChanged(
//            states,
//            sessionContext
//        )
//        this.sessionContext = sessionContext
//    }

    private fun resolveTypeahead(states: InvocationContext, details: List<Triple<DetailContext, String, String>>, selectedIndex: Int, typeahead: String): String {
        val recommendation = details[selectedIndex].first.reformatted.content()
        val userInput = states.recommendationContext.userInputSinceInvocation
        var indexOfFirstNonWhiteSpace = typeahead.indexOfFirst { !it.isWhitespace() }
        if (indexOfFirstNonWhiteSpace == -1) {
            indexOfFirstNonWhiteSpace = typeahead.length
        }

        for (i in 0..indexOfFirstNonWhiteSpace) {
            val subTypeahead = typeahead.substring(i)
            if (recommendation.startsWith(userInput + subTypeahead)) return subTypeahead
        }
        return typeahead
    }

    fun updatePopupPanel(states: InvocationContext?, sessionContext: SessionContext?) {
        if (states == null || sessionContext == null) return
        val userInput = states.recommendationContext.userInputSinceInvocation
//        val details = states.recommendationContext.details
        val selectedIndex = sessionContext.selectedIndex
        val details = CodeWhispererService.getInstance().ongoingRequests.values.filterNotNull().flatMap { element ->
            val context = element.recommendationContext
            context.details.map {
                Tuple3(it, context.userInputSinceInvocation, context.typeaheadOriginal)
            }
        }
        val typeaheadOriginal = details[selectedIndex].v3
        val validCount = getValidCount(emptyList(), userInput, typeaheadOriginal)
        val validSelectedIndex = getValidSelectedIndex(emptyList(), userInput, selectedIndex, typeaheadOriginal)
        updateSelectedRecommendationLabelText(validSelectedIndex, validCount)
        updateNavigationPanel(validSelectedIndex, validCount)
        updateImportPanel(details[selectedIndex].v1.recommendation.mostRelevantMissingImports())
        updateCodeReferencePanel(states.requestContext.project, details[selectedIndex].v1.recommendation.references())
    }

    fun render(
        states: InvocationContext,
        sessionContext: SessionContext,
        overlappingLinesCount: Int,
        isRecommendationAdded: Boolean,
        isScrolling: Boolean
    ) {
        updatePopupPanel(states, sessionContext)

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
            showPopup(states, sessionContext)
            if (!isScrolling) {
                states.requestContext.latencyContext.codewhispererPostprocessingEnd = System.nanoTime()
                states.requestContext.latencyContext.codewhispererEndToEndEnd = System.nanoTime()
            }
        }
        if (isScrolling ||
            CodeWhispererInvocationStatus.getInstance().hasExistingInvocation() ||
            !sessionContext.isFirstTimeShowingPopup
        ) {
            return
        }
        CodeWhispererTelemetryService.getInstance().sendClientComponentLatencyEvent(states)
    }

    fun dontClosePopupAndRun(runnable: () -> Unit) {
        try {
            shouldListenerCancelPopup = false
            runnable()
        } finally {
            shouldListenerCancelPopup = true
        }
    }

    fun resetSession() {
        sessionContext?.let {
            Disposer.dispose(it)
        }
        sessionContext = null
    }

    fun cancelPopup() {
        myPopup?.let {
            it.cancel()
            Disposer.dispose(it)
        }
        myPopup = null
    }

    fun closePopup() {
       myPopup?.let {
            it.closeOk(null)
            Disposer.dispose(it)
        }
        myPopup = null
    }

    fun showPopup(
        states: InvocationContext,
        sessionContext: SessionContext,
        force: Boolean = false,
    ) {
//        popup = initPopup()
        val p = states.requestContext.editor.offsetToXY(sessionContext.popupDisplayOffset)
        var popup: JBPopup? = null
        if (myPopup == null) {
            popup = initPopup()
            initPopupListener(states, sessionContext, popup)
        } else {
            popup = myPopup
        }
//        val popup = myPopup
        if (popup == null) {
            val a = 1
        }
        val editor = states.requestContext.editor
//        val detailContexts = states.recommendationContext.details
        val userInputOriginal = states.recommendationContext.userInputOriginal
//        val userInput = states.recommendationContext.userInputSinceInvocation
        val selectedIndex = sessionContext.selectedIndex
        val details = CodeWhispererService.getInstance().ongoingRequests.values.filterNotNull().flatMap { element ->
            val context = element.recommendationContext
            context.details.map {
                Tuple3(it, context.userInputSinceInvocation, context.typeaheadOriginal)
            }
        }
        val detail = details[selectedIndex].v1
//        val typeahead = details[selectedIndex].v4
        val userInputLines = userInputOriginal.split("\n").size - 1
//        val lineCount = getReformattedRecommendation(detail, userInput).split("\n").size
        val popupSize = (popup as AbstractPopup).preferredContentSize
//        val yBelowLastLine = p.y + (lineCount + additionalLines + userInputLines - overlappingLinesCount) * editor.lineHeight
        val yAboveFirstLine = p.y - popupSize.height + userInputLines * editor.lineHeight
        val popupRect = Rectangle(p.x, yAboveFirstLine, popupSize.width, popupSize.height)
        val editorRect = editor.scrollingModel.visibleArea
        var shouldHidePopup = false

        CodeWhispererInvocationStatus.getInstance().setPopupActive(true)

        // Check if the current editor still has focus. If not, don't show the popup.
        val isSameEditorAsTrigger = if (!AppMode.isRemoteDevHost()) {
            editor.contentComponent.isFocusOwner
        } else {
            FileEditorManager.getInstance(states.requestContext.project).selectedTextEditorWithRemotes.firstOrNull() == editor
        }
        if (!isSameEditorAsTrigger && false) {
            LOG.debug { "Current editor no longer has focus, not showing the popup" }
            Disposer.dispose(sessionContext)
//            cancelPopup()
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
            val perceivedLatency = CodeWhispererInvocationStatus.getInstance().getTimeSinceDocumentChanged()
            CodeWhispererTelemetryService.getInstance().sendPerceivedLatencyEvent(
                detail.requestId,
                states.requestContext,
                states.responseContext,
                perceivedLatency
            )
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
                } else {
//                WindowManager.getInstance().setAlphaModeRatio(popup.popupWindow, 1f)
                }
            }
        }
    }

    fun hidePopup(editor: Editor) {
        val popupWindow = (myPopup as AbstractPopup?)?.popupWindow ?: return
        WindowManager.getInstance().setAlphaModeRatio(popupWindow, 1f)

        val a = ComponentUtil.getWindow(LookupManager.getActiveLookup(editor)?.component)
        if (a != null) {
            WindowManager.getInstance().setAlphaModeRatio(a, 0f)
        }
    }

    fun initPopup(): JBPopup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(popupComponents.panel, null)
        .setAlpha(0.1F)
        .setCancelOnClickOutside(true)
//        .setCancelOnOtherWindowOpen(true)
        .setCancelKeyEnabled(true)
        .setCancelOnWindowDeactivation(true)
        .createPopup().also {
            myPopup = it
        }

    fun getReformattedRecommendation(detailContext: DetailContext, userInput: String) =
        detailContext.reformatted.content().substring(userInput.length)

    fun initPopupListener(states: InvocationContext, sessionContext: SessionContext, popup: JBPopup) {
        addPopupListener(states, sessionContext, popup)
        states.requestContext.editor.scrollingModel.addVisibleAreaListener(CodeWhispererScrollListener(states, sessionContext), sessionContext)
        addButtonActionListeners(states, sessionContext)
        addMessageSubscribers(states, sessionContext)
        setPopupActionHandlers(states, sessionContext)
        addComponentListeners(states, sessionContext)
    }

    private fun addPopupListener(states: InvocationContext, sessionContext: SessionContext, popup: JBPopup) {
        val listener = CodeWhispererPopupListener(states, sessionContext)
        popup.addListener(listener)
        Disposer.register(popup) {
            println("listener is removed")
            popup.removeListener(listener)
        }
    }

    private fun addMessageSubscribers(states: InvocationContext, sessionContext: SessionContext) {
        val connect = ApplicationManager.getApplication().messageBus.connect(sessionContext)
        connect.subscribe(
            CODEWHISPERER_USER_ACTION_PERFORMED,
            object : CodeWhispererUserActionListener {
                override fun navigateNext(states: InvocationContext) {
                    changeStatesForNavigation(states, 1)
                }

                override fun navigatePrevious(states: InvocationContext) {
                    changeStatesForNavigation(states, -1)
                }

                override fun backspace(states: InvocationContext, diff: String) {
                    changeStatesForTypeahead(states, diff, false)
                }

                override fun enter(states: InvocationContext, diff: String) {
                    changeStatesForTypeahead(states, diff, true)
                }

                override fun type(states: InvocationContext, diff: String) {
                    // remove the character at primaryCaret if it's the same as the typed character
                    val caretOffset = states.requestContext.editor.caretModel.primaryCaret.offset
                    val document = states.requestContext.editor.document
                    val text = document.charsSequence
                    if (caretOffset < text.length && diff == text[caretOffset].toString()) {
                        WriteCommandAction.runWriteCommandAction(states.requestContext.project) {
                            document.deleteString(caretOffset, caretOffset + 1)
                        }
                    }
                    changeStatesForTypeahead(states, diff, true)
                }

                override fun beforeAccept(states: InvocationContext, sessionContext: SessionContext) {
                    dontClosePopupAndRun {
                        CodeWhispererEditorManager.getInstance().updateEditorWithRecommendation(states, sessionContext)
                    }
                    closePopup()
                }
            }
        )
    }

    private fun addButtonActionListeners(states: InvocationContext, sessionContext: SessionContext) {
        popupComponents.prevButton.addButtonActionListener(CodeWhispererPrevButtonActionListener(states), sessionContext)
        popupComponents.nextButton.addButtonActionListener(CodeWhispererNextButtonActionListener(states), sessionContext)
        popupComponents.acceptButton.addButtonActionListener(CodeWhispererAcceptButtonActionListener(states, sessionContext), sessionContext)
    }

    private fun JButton.addButtonActionListener(listener: CodeWhispererActionListener, sessionContext: SessionContext) {
        this.addActionListener(listener)
        Disposer.register(sessionContext) { this.removeActionListener(listener) }
    }

    private fun setPopupActionHandlers(states: InvocationContext, sessionContext: SessionContext) {
        val actionManager = EditorActionManager.getInstance()
        setPopupTypedHandler(CodeWhispererPopupTypedHandler(TypedAction.getInstance().rawHandler, states), sessionContext)
        setPopupActionHandler(ACTION_EDITOR_TAB, CodeWhispererPopupTabHandler(states, sessionContext), sessionContext)
        setPopupActionHandler(ACTION_EDITOR_MOVE_CARET_LEFT, CodeWhispererPopupLeftArrowHandler(states), sessionContext)
        setPopupActionHandler(ACTION_EDITOR_MOVE_CARET_RIGHT, CodeWhispererPopupRightArrowHandler(states), sessionContext)
        setPopupActionHandler(
            ACTION_EDITOR_ENTER,
            CodeWhispererPopupEnterHandler(actionManager.getActionHandler(ACTION_EDITOR_ENTER), states),
            sessionContext
        )
        setPopupActionHandler(
            ACTION_EDITOR_BACKSPACE,
            CodeWhispererPopupBackspaceHandler(actionManager.getActionHandler(ACTION_EDITOR_BACKSPACE), states),
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

    private fun addComponentListeners(states: InvocationContext, sessionContext: SessionContext) {
        val editor = states.requestContext.editor
        val codewhispererSelectionListener: SelectionListener = object : SelectionListener {
            override fun selectionChanged(event: SelectionEvent) {
                if (shouldListenerCancelPopup) {
                    cancelPopup()
                }
                super.selectionChanged(event)
            }
        }
        editor.selectionModel.addSelectionListener(codewhispererSelectionListener)
        Disposer.register(sessionContext) { editor.selectionModel.removeSelectionListener(codewhispererSelectionListener) }

        val codewhispererDocumentListener: DocumentListener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val statesWithin = states
                if (shouldListenerCancelPopup) {
                    // handle IntelliSense accept case
                    if (editor.document == event.document &&
                        editor.caretModel.offset == event.offset &&
                        event.newLength > event.oldLength) {
                        dontClosePopupAndRun {
                            super.documentChanged(event)
                            editor.caretModel.moveCaretRelatively(event.newLength, 0, false, false, true)
                            changeStatesForTypeahead(states, event.newFragment.toString(), true)
                        }
                        return
                    } else {
                        cancelPopup()
                    }
                }
                super.documentChanged(event)
            }
        }
        editor.document.addDocumentListener(codewhispererDocumentListener, states)

        val codewhispererCaretListener: CaretListener = object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
//                if (shouldListenerCancelPopup) {
//                    cancelPopup()
//                }
                super.caretPositionChanged(event)
            }
        }
        editor.caretModel.addCaretListener(codewhispererCaretListener)
        Disposer.register(sessionContext) { editor.caretModel.removeCaretListener(codewhispererCaretListener) }

        val editorComponent = editor.contentComponent
        if (editorComponent.isShowing) {
            val window = ComponentUtil.getWindow(editorComponent)
            val windowListener: ComponentListener = object : ComponentAdapter() {
                override fun componentMoved(event: ComponentEvent) {
                    cancelPopup()
                }

                override fun componentShown(e: ComponentEvent?) {
                    cancelPopup()
                    super.componentShown(e)
                }
            }
            window?.addComponentListener(windowListener)
            Disposer.register(states) { window?.removeComponentListener(windowListener) }
        }


        val suggestionHoverEnterListener: EditorMouseMotionListener = object : EditorMouseMotionListener {
            override fun mouseMoved(e: EditorMouseEvent) {
                e.mouseEvent.component
                println("current mouse offset : ${e.offset}, point: ${e.mouseEvent.point}")
                val startOffset = editor.offsetToXY(editor.caretModel.offset)
                println("caret x y: ${startOffset}")
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
                    showPopup(states, sessionContext, force = true)
                } else {
                    hidePopup(editor)
                }
                super.mouseMoved(e)
            }
        }
        editor.addEditorMouseMotionListener(suggestionHoverEnterListener, states)
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

    fun hasConflictingPopups(editor: Editor): Boolean =
        (ParameterInfoController.existsWithVisibleHintForEditor(editor, true) ||
            LookupManager.getActiveLookup(editor) != null) && false

    fun findNewSelectedIndex(
        isReverse: Boolean,
        start: Int
    ): Int {
        val recos = CodeWhispererService.getInstance().ongoingRequests.values.filterNotNull().flatMap { element ->
            element.recommendationContext.details.map {
                Triple(it, element.recommendationContext.userInputSinceInvocation, element.recommendationContext.typeaheadOriginal)
            }
        }



//            .flatMap { Triple(it.recommendationContext.details, it.recommendationContext. }
        val count = recos.size
        val unit = if (isReverse) -1 else 1
        var currIndex: Int
        for (i in 0 until count) {
            currIndex = (start + i * unit) % count
            if (currIndex < 0) {
                currIndex += count
            }
            val triple = recos[currIndex]
            if (isValidRecommendation(triple.first, triple.second, triple.third)) {
                return currIndex
            }
        }

//        val count = detailContexts.size
//        val unit = if (isReverse) -1 else 1
//        var currIndex: Int
//        for (i in 0 until count) {
//            currIndex = (start + i * unit) % count
//            if (currIndex < 0) {
//                currIndex += count
//            }
//            if (isValidRecommendation(detailContexts[currIndex], userInput, typeahead)) {
//                return currIndex
//            }
//        }
        return -1
    }

    private fun getValidCount(detailContexts: List<DetailContext>, userInput: String, typeahead: String): Int {
        var count = 0
        CodeWhispererService.getInstance().ongoingRequests.forEach { t, u ->
            if (u == null) return@forEach
            count += u.recommendationContext.details.filter {
                isValidRecommendation(it, u.recommendationContext.userInputSinceInvocation, u.recommendationContext.typeaheadOriginal)
            }.size
        }
        return count
//        detailContexts.filter { isValidRecommendation(it, userInput, typeahead) }.size
    }

    private fun getValidSelectedIndex(
        detailContexts: List<DetailContext>,
        userInput: String,
        selectedIndex: Int,
        typeahead: String
    ): Int {
        var curr = 0

        val details = CodeWhispererService.getInstance().ongoingRequests.values.filterNotNull().flatMap { element ->
            val context = element.recommendationContext
            context.details.map {
                Triple(it, context.userInputSinceInvocation, context.typeaheadOriginal)
            }
        }
        details.forEachIndexed { index, triple ->
            if (index == selectedIndex) {
                return curr
            }
            if (isValidRecommendation(triple.first, triple.second, triple.third)) {
                curr++
            }

        }
        return -1
    }

    private fun isValidRecommendation(detailContext: DetailContext, userInput: String, typeahead: String): Boolean {
        if (detailContext.isDiscarded) return false
        if (detailContext.recommendation.content().isEmpty()) return false
        return detailContext.recommendation.content().startsWith(userInput + typeahead)
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
    fun stateChanged(states: InvocationContext, sessionContext: SessionContext) {}
    fun scrolled(states: InvocationContext, sessionContext: SessionContext) {}
    fun recommendationAdded(states: InvocationContext, sessionContext: SessionContext) {}
}

interface CodeWhispererUserActionListener {
    fun backspace(states: InvocationContext, diff: String) {}
    fun enter(states: InvocationContext, diff: String) {}
    fun type(states: InvocationContext, diff: String) {}
    fun navigatePrevious(states: InvocationContext) {}
    fun navigateNext(states: InvocationContext) {}
    fun beforeAccept(states: InvocationContext, sessionContext: SessionContext) {}
    fun afterAccept(states: InvocationContext, details: List<Tuple3<DetailContext, String, String>>, sessionContext: SessionContext, rangeMarker: RangeMarker) {}
}
