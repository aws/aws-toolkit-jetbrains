// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.ui.components

import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeList
import com.intellij.openapi.vcs.changes.patch.ApplyPatchDifferentiatedDialog
import com.intellij.openapi.vcs.changes.patch.ApplyPatchExecutor
import com.intellij.openapi.vcs.changes.patch.ApplyPatchMode
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFilePatch
import com.intellij.openapi.vfs.VirtualFile
import software.aws.toolkits.jetbrains.services.codemodernizer.model.JobId
import software.aws.toolkits.jetbrains.services.codemodernizer.state.CodeTransformTelemetryState
import software.aws.toolkits.telemetry.CodeTransformPatchViewerCancelSrcComponents
import software.aws.toolkits.telemetry.CodetransformTelemetry

class DiffPatchDialog(
    project: Project,
    callback: ApplyPatchExecutor<*>,
    executors: List<ApplyPatchExecutor<*>>,
    applyPatchMode: ApplyPatchMode,
    patchFile: VirtualFile? = null,
    patches: List<FilePatch>? = null,
    defaultList: ChangeList? = null,
    binaryShelvedPatches: List<ShelvedBinaryFilePatch>? = null,
    preselectedChanges: Collection<Change>? = null,
    externalCommitMessage: String? = null,
    useProjectRootAsPredefinedBase: Boolean,
    private val jobId: JobId,
) : ApplyPatchDifferentiatedDialog(
    project,
    callback,
    executors,
    applyPatchMode,
    patchFile,
    patches,
    defaultList,
    binaryShelvedPatches,
    preselectedChanges,
    externalCommitMessage,
    useProjectRootAsPredefinedBase
) {
    override fun doCancelAction() {
        super.doCancelAction()
        CodetransformTelemetry.vcsViewerCanceled(
            codeTransformPatchViewerCancelSrcComponents = CodeTransformPatchViewerCancelSrcComponents.CancelButton,
            codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
            codeTransformJobId = jobId.id
        )
    }

    override fun doOKAction() {
        super.doOKAction()
        CodetransformTelemetry.vcsViewerSubmitted(
            codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
            codeTransformJobId = jobId.id
        )
    }

    override fun show() {
        CodetransformTelemetry.vcsDiffViewerVisible(
            codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
            codeTransformJobId = jobId.id
        )
        super.show()
    }
}
