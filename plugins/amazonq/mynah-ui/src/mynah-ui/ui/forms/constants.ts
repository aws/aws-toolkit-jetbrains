/*!
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

export const enum FormButtonIds {
  CodeTransformInputConfirm = 'codetransform-input-confirm',
  CodeTransformInputSQLMetadata = 'codetransform-input-select-sql-metadata',
  CodeTransformInputSQLModuleSchema = 'codetransform-input-select-sql-module-schema',
  CodeTransformInputCancel = 'codetransform-input-cancel',
  CodeTransformInputSkipTests = 'codetransform-input-confirm-skip-tests',
  OpenMvnBuild = 'open_mvn_build',
  StopTransform = 'stop_transform',
  OpenTransformationHub = 'open_transformation_hub',
  CodeTransformViewDiff = 'view_diff',
  CodeTransformViewSummary = 'view_summary',
  CodeTransformViewBuildLog = 'view_build_log',
  ConfirmHilSelection = 'confirm_hil_selection',
  RejectHilSelection = 'reject_hil_selection',
  OpenDependencyErrorPom = "open_dependency_error_pom",
}

export const isFormButtonCodeTransform = (id: string): boolean => {
  return (
    id === FormButtonIds.CodeTransformInputConfirm ||
    id === FormButtonIds.CodeTransformInputCancel ||
    id === FormButtonIds.CodeTransformInputSQLMetadata ||
    id === FormButtonIds.CodeTransformInputSQLModuleSchema ||
    id === FormButtonIds.CodeTransformInputSkipTests ||
    id === FormButtonIds.CodeTransformViewDiff ||
    id === FormButtonIds.CodeTransformViewSummary ||
    id === FormButtonIds.CodeTransformViewBuildLog ||
    id === FormButtonIds.OpenMvnBuild ||
    id === FormButtonIds.StopTransform ||
    id === FormButtonIds.OpenTransformationHub ||
    id === FormButtonIds.ConfirmHilSelection ||
    id === FormButtonIds.RejectHilSelection ||
    id === FormButtonIds.OpenDependencyErrorPom
  )
}
