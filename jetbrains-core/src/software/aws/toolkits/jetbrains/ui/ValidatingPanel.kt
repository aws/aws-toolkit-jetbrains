// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.layout.LayoutBuilder
import com.intellij.ui.layout.RowBuilder
import com.intellij.ui.layout.panel
import com.intellij.util.Alarm
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Component
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JComponent
import kotlin.reflect.jvm.jvmName

class ValidatingPanel internal constructor(parentDisposable: Disposable, private val contentPanel: DialogPanel, panelActions: List<PanelAction>) :
    BorderLayoutPanel() {
    private val disposable = Disposer.newDisposable(parentDisposable, this::class.jvmName)
    private val buttonActions = createButtonActions(panelActions)

    // Used for the validateOnApply checking
    private val validateCallbacks = contentPanel.validateCallbacks.toList()
    private val validationAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, disposable)
    private var previousErrors = emptyList<ValidationInfo>()
    private var validatorStarted = false
    private var performingAction = false

    init {
        // Used for the validateOnInput checking
        contentPanel.registerValidators(disposable) { map ->
            updateActionButtons(map.isEmpty())
        }

        addToCenter(contentPanel)
        addToBottom(createActionsPanel())
    }

    private fun createButtonActions(panelActions: List<PanelAction>) = panelActions.associateBy(
        keySelector = { it.name },
        valueTransform = {
            if (it.requiresValidation) {
                ValidatingAction(it.name, it.actionListener)
            } else {
                ButtonAction(it.name, it.actionListener)
            }
        }
    )

    private fun createActionsPanel(): Component = panel {
        row {
            buttonActions.forEach {
                component(JButton(it.value))
            }
        }
    }

    fun getAction(name: String): ButtonAction? = buttonActions[name]

    private fun updateActionButtons(panelIsValid: Boolean) {
        buttonActions.values.filterIsInstance<ValidatingAction>().forEach { it.isEnabled = panelIsValid }
    }

    private fun performValidation(): List<ValidationInfo> {
        if (validateCallbacks.isNotEmpty()) {
            val result = mutableListOf<ValidationInfo>()
            for (callback in validateCallbacks) {
                callback.invoke()?.let {
                    result.add(it)
                }
            }
            return result
        }
        return emptyList()
    }

    private fun updateErrorInfo(info: List<ValidationInfo>) {
        val updateNeeded = previousErrors != info
        if (updateNeeded) {
            runOnUi {
                setErrorInfoAll(info)
                updateActionButtons(info.all { it.okEnabled })
            }
        }
    }

    fun getPreferredFocusedComponent(): JComponent? = contentPanel.preferredFocusedComponent

    private fun startTrackingValidation() {
        runOnUi {
            if (!validatorStarted) {
                validatorStarted = true
                initValidation()
            }
        }
    }

    private fun initValidation() {
        validationAlarm.cancelAllRequests()
        val validateRequest = Runnable {
            if (!isDisposed()) {
                updateErrorInfo(performValidation())
                initValidation()
            }
        }
        validationAlarm.addRequest(validateRequest, VALIDATION_INTERVAL, ModalityState.stateForComponent(this))
    }

    open inner class ButtonAction internal constructor(name: String, private val listener: (event: ActionEvent) -> Unit) : AbstractAction(name) {
        override fun actionPerformed(e: ActionEvent) {
            if (performingAction) return
            try {
                performingAction = true
                doAction(e)
            } finally {
                performingAction = false
            }
        }

        protected open fun doAction(e: ActionEvent) {
            listener.invoke(e)
        }
    }

    private inner class ValidatingAction internal constructor(name: String, listener: (ActionEvent) -> Unit) : ButtonAction(name, listener) {
        override fun doAction(e: ActionEvent) {
            val errorList = performValidation()
            if (errorList.isNotEmpty()) {
                // Give the first error focus
                val info = errorList.first()
                info.component?.let {
                    IdeFocusManager.getInstance(null).requestFocus(it, true)
                }

                updateErrorInfo(errorList)
                startTrackingValidation()
            } else {
                contentPanel.apply()

                super.doAction(e)
            }
        }
    }

    private fun setErrorInfoAll(latestErrors: List<ValidationInfo>) {
        if (previousErrors == latestErrors) return

        // Remove corrected errors
        previousErrors.asSequence()
            .filterNot { latestErrors.contains(it) }
            .mapNotNull {
                it.component?.let { c ->
                    ComponentValidator.getInstance(c)?.orElseGet(null)
                }
            }
            .forEach { it.updateInfo(null) }

        previousErrors = latestErrors
        previousErrors.forEach {
            it.component?.let { c ->
                val validator = ComponentValidator.getInstance(c).orElseGet {
                    ComponentValidator(disposable).installOn(c)
                }

                validator.updateInfo(it)
            }
        }
    }

    private fun isDisposed(): Boolean = Disposer.isDisposed(disposable)

    private fun runOnUi(action: () -> Unit) {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            action()
        } else {
            application.invokeLater(action, ModalityState.stateForComponent(this)) { isDisposed() }
        }
    }

    private companion object {
        const val VALIDATION_INTERVAL = 300
    }
}

interface ValidatingPanelBuilder : RowBuilder {
    fun actions(isVertical: Boolean = false, init: ValidatingActionsBuilder.() -> Unit)
}

class ValidatingPanelBuilderImpl(private val contentBuilder: LayoutBuilder) :
    ValidatingPanelBuilder,
    RowBuilder by contentBuilder {
    internal val actions = mutableListOf<PanelAction>()

    override fun actions(isVertical: Boolean, init: ValidatingActionsBuilder.() -> Unit) {
        ValidatingActionsBuilder(this@ValidatingPanelBuilderImpl).init()
    }

    fun build(parentDisposable: Disposable, contentPanel: DialogPanel): ValidatingPanel = ValidatingPanel(parentDisposable, contentPanel, actions)
}

class ValidatingActionsBuilder(private val validatingPanelBuilder: ValidatingPanelBuilderImpl) {
    fun addAction(name: String, requiresValidation: Boolean = true, actionListener: (event: ActionEvent) -> Unit) {
        validatingPanelBuilder.actions.add(PanelAction(name, requiresValidation, actionListener))
    }
}

@PublishedApi
internal data class PanelAction(val name: String, val requiresValidation: Boolean, val actionListener: (event: ActionEvent) -> Unit)

fun validatingPanel(disposable: Disposable, init: ValidatingPanelBuilder.() -> Unit): ValidatingPanel {
    val ref = Ref<ValidatingPanelBuilderImpl>()
    val contentPanel = panel {
        val builder = ValidatingPanelBuilderImpl(this)
        builder.init()

        ref.set(builder)
    }

    return ref.get().build(disposable, contentPanel)
}
