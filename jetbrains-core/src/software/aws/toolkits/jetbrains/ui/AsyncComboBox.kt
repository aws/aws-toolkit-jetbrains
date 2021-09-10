// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.SimpleListCellRenderer
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.TestOnly
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.spinUntil
import software.aws.toolkits.jetbrains.utils.ui.selected
import java.awt.Component
import java.time.Duration
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.DefaultComboBoxModel
import javax.swing.JList
import javax.swing.event.ListDataEvent
import kotlin.concurrent.timerTask

class AsyncComboBox<T>(
    private val comboBoxModel: DefaultComboBoxModel<T> = DefaultComboBoxModel(),
    customizer: SimpleListCellRenderer.Customizer<in T>? = null
) : ComboBox<T>(comboBoxModel), Disposable {
    private val loading = AtomicBoolean(false)
    private val scope = ApplicationThreadPoolScope("oh", this)
    init {
        renderer = object : SimpleListCellRenderer<T>() {
            override fun getListCellRendererComponent(
                list: JList<out T>?,
                value: T?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean
            ): Component {
                val component = super.getListCellRendererComponent(list, value, index, selected, hasFocus) as SimpleListCellRenderer<*>

                if (loading.get() && index == -1) {
                    component.icon = AnimatedIcon.Default.INSTANCE
                    component.text = "Loading"
                }

                return component
            }

            override fun customize(list: JList<out T>, value: T, index: Int, selected: Boolean, hasFocus: Boolean) {
                customizer?.customize(this, value, index)
            }
        }
    }

    private val reloadTimer = Timer()
    private var reloadTimerTask: TimerTask? = null

    @Synchronized
    fun proposeModelUpdate(modelConsumer: suspend (DefaultComboBoxModel<T>) -> Unit) {
        loading.set(true)
        removeAllItems()
        reloadTimerTask?.cancel()
        // debounce
        reloadTimerTask = timerTask {
            val event = ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED, -1, -1)
            // force redraw of renderer without actually touching the underlying model
            comboBoxModel.listDataListeners?.forEach { it.contentsChanged(event) }
            runBlocking(scope.coroutineContext) {
                try {
                    modelConsumer.invoke(comboBoxModel)
                } finally {
                    loading.set(false)
                }
            }
            comboBoxModel.listDataListeners?.forEach { it.contentsChanged(event) }
        }
        reloadTimer.schedule(reloadTimerTask, 350)
    }

    override fun dispose() {
    }

    override fun getSelectedItem(): Any? {
        if (loading.get()) {
            return null
        }
        return super.getSelectedItem()
    }

    @TestOnly
    @Synchronized
    internal fun blockingGet(duration: Duration): T? {
        try {
            spinUntil(duration) { !loading.get() }
        } catch (e: Exception) {
            return null
        }

        return selected()
    }

    override fun setSelectedItem(anObject: Any?) {
        if (loading.get()) {
            return
        }
        super.setSelectedItem(anObject)
    }
}
