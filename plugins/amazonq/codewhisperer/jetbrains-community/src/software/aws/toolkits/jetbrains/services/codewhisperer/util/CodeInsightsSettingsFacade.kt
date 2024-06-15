// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.util

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SimpleModificationTracker
import software.aws.toolkits.core.utils.getLogger
import kotlin.reflect.KMutableProperty

class CodeInsightsSettingsFacade : SimpleModificationTracker(), Disposable {
    private val settings = CodeInsightSettings.getInstance()

    private var pendingReverts = mutableListOf<ChangeAndRevert<*>>()

    inner class ChangeAndRevert<T : Any>(
        val p: KMutableProperty<T>,
        val value: T
    ) {
        val origin: T = p.getter.call()
        var isComplete: Boolean = false
            private set

        fun commit():  ChangeAndRevert<T> {
            p.setter.call(value)
            return this
        }

        fun revert() {
            if (isComplete) {
                return
            }

            p.setter.call(origin)
            isComplete = true
        }

        fun registerDisposable(disposable: Disposable) {
            Disposer.register(disposable) {
                revert()
            }
        }
    }

    private fun revertAll() {
        if (pendingReverts.count { !it.isComplete } == 0) {
            return
        }

        pendingReverts.forEach {
            it.revert()
        }

        pendingReverts = pendingReverts.filter {
            !it.isComplete
        }.toMutableList()
    }

    fun disableCodeInsightUntil(parentDisposable: Disposable) {
        revertAll()

        ChangeAndRevert(settings::TAB_EXITS_BRACKETS_AND_QUOTES, false).apply {
            registerDisposable(parentDisposable)
            pendingReverts.add(this)
        }.commit()

        ChangeAndRevert(settings::AUTO_POPUP_COMPLETION_LOOKUP, false).apply {
            registerDisposable(parentDisposable)
            pendingReverts.add(this)
        }.commit()

        Disposer.register(parentDisposable) {
            revertAll()
        }
    }

    override fun dispose() {
        pendingReverts.forEach { it.revert() }
    }

    companion object {
        val LOG = getLogger<CodeInsightsSettingsFacade>()
    }
}
