// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.components.telemetry

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import software.aws.toolkits.core.telemetry.TelemetryNamespace
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService
import javax.swing.Icon

// Constructor signatures:
//  public AnAction(){
//  }
//  public AnAction(Icon icon){
//    this(null, null, icon);
//  }
//  public AnAction(@Nullable String text) {
//    this(text, null, null);
//  }
//  public AnAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
//    <logic>
//  }
abstract class AnActionWrapper : TelemetryNamespace, AnAction {
    constructor(): super()
    constructor(text: String? = null, description: String? = null, icon: Icon? = null):
        super(text, description, icon)

    /**
     * Consumers should use doActionPerformed(e: AnActionEvent)
     */
    final override fun actionPerformed(e: AnActionEvent) {
        doActionPerformed(e)
        telemetry.record(getNamespace())
    }

    abstract fun doActionPerformed(e: AnActionEvent)

    companion object {
        val telemetry = TelemetryService.getInstance()
    }
}

abstract class ComboBoxActionWrapper : TelemetryNamespace, ComboBoxAction() {
    /**
     * Consumers should use doActionPerformed(e: AnActionEvent)
     */
    final override fun actionPerformed(e: AnActionEvent) {
        doActionPerformed(e)
        telemetry.record(getNamespace())
    }

    open fun doActionPerformed(e: AnActionEvent) = super.actionPerformed(e)

    companion object {
        val telemetry = TelemetryService.getInstance()
    }
}

abstract class ToogleActionWrapper : TelemetryNamespace, ToggleAction {
    constructor(): super()
    constructor(text: String? = null, description: String? = null, icon: Icon? = null):
        super(text, description, icon)

    final override fun isSelected(e: AnActionEvent): Boolean {
        telemetry.record(getNamespace())
        return doIsSelected(e)
    }

    final override fun setSelected(e: AnActionEvent, state: Boolean) {
        doSetSelected(e, state)
        telemetry.record(getNamespace())
    }

    abstract fun doIsSelected(e: AnActionEvent): Boolean

    abstract fun doSetSelected(e: AnActionEvent, state: Boolean)

    companion object {
        val telemetry = TelemetryService.getInstance()
    }
}