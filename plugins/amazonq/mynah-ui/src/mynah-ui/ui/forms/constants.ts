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
  CodeTransformInputOneOrMultipleDiffs = 'codetransform-input-confirm-one-or-multiple-diffs',
  OpenMvnBuild = 'open_mvn_build',
  StopTransform = 'stop_transform',
  OpenTransformationHub = 'open_transformation_hub',
  CodeTransformViewDiff = 'view_diff',
  CodeTransformViewSummary = 'view_summary',
  CodeTransformViewBuildLog = 'view_build_log',
  ConfirmHilSelection = 'confirm_hil_selection',
  RejectHilSelection = 'reject_hil_selection',
  OpenDependencyErrorPom = "open_dependency_error_pom",
  CodeScanStartProjectScan = "codescan_start_project_scan",
  CodeScanStartFileScan = "codescan_start_file_scan",
  CodeScanStopProjectScan = "codescan_stop_project_scan",
  CodeScanStopFileScan = "codescan_stop_file_scan",
  CodeScanOpenIssues = "codescan_open_issues",
  CodeTestStartGeneration = "code_test_start_generation",
  CodeTestViewDiff = "utg_view_diff",
  CodeTestAccept = "utg_accept",
  CodeTestProvideFeedback = "utg_feedback",
  CodeTestRegenerate = "utg_regenerate",
  CodeTestReject = "utg_reject",
  CodeTestBuildAndExecute = "utg_build_and_execute",
  CodeTestModifyCommand = "utg_modify_command",
  CodeTestSkipAndFinish = "utg_skip_and_finish",
  CodeTestInstallAndContinue = "utg_install_and_continue",
  CodeTestRejectAndRevert = "utg_reject_and_revert",
  CodeTestProceed = "utg_proceed",

}

export const isFormButtonCodeTransform = (id: string): boolean => {
  return (
    id === FormButtonIds.CodeTransformInputConfirm ||
    id === FormButtonIds.CodeTransformInputCancel ||
    id === FormButtonIds.CodeTransformInputSQLMetadata ||
    id === FormButtonIds.CodeTransformInputSQLModuleSchema ||
    id === FormButtonIds.CodeTransformInputSkipTests ||
    id === FormButtonIds.CodeTransformInputOneOrMultipleDiffs ||
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

export const isFormButtonCodeTest = (id: string): boolean => {
    return (
        id === FormButtonIds.CodeTestStartGeneration || id.startsWith("utg")
    )
}

export const isFormButtonCodeScan = (id: string): boolean => {
    return (
        id === FormButtonIds.CodeScanStartProjectScan ||
        id === FormButtonIds.CodeScanStartFileScan ||
        id === FormButtonIds.CodeScanStopProjectScan ||
        id === FormButtonIds.CodeScanStopFileScan ||
        id === FormButtonIds.CodeScanOpenIssues
    )
}
