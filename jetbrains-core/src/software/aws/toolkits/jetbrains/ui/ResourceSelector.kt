// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.AwsResourceCache
import software.aws.toolkits.jetbrains.core.Resource
import software.aws.toolkits.jetbrains.utils.ui.selected
import software.aws.toolkits.resources.message
import javax.swing.DefaultComboBoxModel

class ResourceSelector<T>(project: Project, private val resourceType: Resource<out Collection<T>>) : ComboBox<T>() {

    private val resourceCache = AwsResourceCache.getInstance(project)
    private val actualModel = this.model as DefaultComboBoxModel<T>
    private var loadingStatus: Status = Status.SUCCESSFUL
    private var shouldBeEnabled: Boolean = true

    @JvmOverloads
    fun load(
        default: T? = null,
        defaultMatcher: ((T) -> Boolean)? = null,
        forceFetch: Boolean = false
    ) {
        val previouslySelected = actualModel.selectedItem
        loadingStatus = Status.LOADING
        runInEdt(ModalityState.any()) {
            super.setEnabled(false)
            setEditable(true)
            selectedItem = message("loading_resource.loading")

            resourceCache.getResource(resourceType, forceFetch = forceFetch).whenComplete { value, error ->
                when {
                    value != null -> {
                        loadingStatus = Status.SUCCESSFUL
                        runInEdt(ModalityState.any()) {
                            setEditable(false)
                            actualModel.removeAllElements()
                            value.sortedBy { it.toString().toLowerCase() }.forEach { actualModel.addElement(it) }
                            super.setEnabled(shouldBeEnabled)
                            selectedItem = when {
                                default != null -> default
                                defaultMatcher != null -> value.find(defaultMatcher) ?: previouslySelected
                                else -> previouslySelected
                            }
                        }
                    }
                    error != null -> {
                        loadingStatus = Status.FAILED
                        val message = message("loading_resource.failed")
                        LOG.warn(error) { message }
                        runInEdt(ModalityState.any()) {
                            selectedItem = message
                            toolTipText = error.message
                        }
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun selected(): T? = if (loadingStatus == Status.SUCCESSFUL) this.selectedItem as? T else null

    @Synchronized
    override fun setEnabled(enabled: Boolean) {
        if (loadingStatus == Status.SUCCESSFUL || !enabled) {
            super.setEnabled(enabled)
        }
        shouldBeEnabled = enabled
    }

    private enum class Status {
        LOADING,
        FAILED,
        SUCCESSFUL
    }

    private companion object {
        val LOG = getLogger<ResourceSelector<*>>()
    }
}