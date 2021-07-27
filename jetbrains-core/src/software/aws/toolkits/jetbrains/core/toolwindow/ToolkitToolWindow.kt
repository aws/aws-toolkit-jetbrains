// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import software.aws.toolkits.jetbrains.utils.getCoroutineUiContext
import javax.swing.JComponent

interface ToolkitToolWindow {
    val toolWindow: ToolWindow

    fun addTab(
        title: String,
        component: JComponent,
        activate: Boolean = false,
        id: String = title,
        disposeLater: Disposable? = null,
        refresh: () -> Unit = { }
    ): Content

    fun removeContent(content: Content)

    fun find(id: String): Content?

    // prefix is prefix of the id. Assumes the window is using id composed of paths, like: "loggroup/logstream"
    fun findPrefix(prefix: String): List<Content>
}

abstract class AbstractToolkitToolWindow {
    internal class DefaultToolkitToolWindow(override val toolWindow: ToolWindow) : ToolkitToolWindow {
        override fun addTab(
            title: String,
            component: JComponent,
            activate: Boolean,
            id: String,
            disposeLater: Disposable?,
            refresh: () -> Unit
        ): Content {
            val contentManager = toolWindow.contentManager
            val content = contentManager.factory.createContent(component, title, false).also {
                it.isCloseable = true
                it.isPinnable = true
                it.putUserData(AWS_TOOLKIT_TOOL_WINDOW_KEY, toolWindow)
                it.putUserData(AWS_TOOLKIT_TAB_ID_KEY, id)
                it.putUserData(AWS_TOOLKIT_TAB_REFRESH_FUNCTION_KEY, refresh)
            }

            contentManager.addContent(content)
            disposeLater?.let { Disposer.register(content, it) }
            if (activate) {
                content.show()
            }

            return content
        }

        override fun removeContent(content: Content) {
            runInEdt {
                toolWindow.contentManager.removeContent(content, true)
            }
        }

        override fun findPrefix(prefix: String): List<Content> =
            toolWindow.contentManager.contents.filter {
                val key = it.getUserData(AWS_TOOLKIT_TAB_ID_KEY) ?: ""
                key.startsWith("$prefix/") || key == prefix
            }

        override fun find(id: String): Content? =
            toolWindow.contentManager.contents.find { id == it.getUserData(AWS_TOOLKIT_TAB_ID_KEY) }

        override fun equals(other: Any?): Boolean =
            (other as? DefaultToolkitToolWindow)?.toolWindow?.equals(toolWindow) == true

        override fun hashCode(): Int =
            toolWindow.hashCode()
    }

    companion object {
        internal val AWS_TOOLKIT_TOOL_WINDOW_KEY = Key.create<ToolWindow>("awsToolkitToolWindow")
        internal val AWS_TOOLKIT_TAB_ID_KEY = Key.create<String>("awsToolkitTabId")
        internal val AWS_TOOLKIT_TAB_REFRESH_FUNCTION_KEY = Key.create<() -> Unit>("awsToolkitRefreshFunction")

        internal fun getOrCreateToolWindow(project: Project, registrationTask: RegisterToolWindowTask): ToolkitToolWindow {
            val toolWindowManager = ToolWindowManager.getInstance(project)
            val window = toolWindowManager.getToolWindow(registrationTask.id) ?: run {
                runBlocking {
                    withContext(getCoroutineUiContext()) {
                        toolWindowManager.registerToolWindow(registrationTask).also {
                            it.installWatcher(it.contentManager)
                        }
                    }
                }
            }

            return DefaultToolkitToolWindow(window)
        }

        fun Content.show(refresh: Boolean = false) {
            this.getUserData(AWS_TOOLKIT_TOOL_WINDOW_KEY)?.let { toolWindow ->
                toolWindow.activate(null, true)
                toolWindow.contentManager.setSelectedContent(this)
            }

            if (refresh) {
                this.getUserData(AWS_TOOLKIT_TAB_REFRESH_FUNCTION_KEY)?.invoke()
            }
        }
    }
}
