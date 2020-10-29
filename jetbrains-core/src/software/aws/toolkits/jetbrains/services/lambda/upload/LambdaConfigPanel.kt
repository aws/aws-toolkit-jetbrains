// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.lambda.upload

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SortedComboBoxModel
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.credentials.AwsConnectionManager
import software.aws.toolkits.jetbrains.services.iam.CreateIamRoleDialog
import software.aws.toolkits.jetbrains.services.iam.IamResources
import software.aws.toolkits.jetbrains.services.iam.IamRole
import software.aws.toolkits.jetbrains.services.lambda.LambdaWidgets.lambdaMemory
import software.aws.toolkits.jetbrains.services.lambda.LambdaWidgets.lambdaTimeout
import software.aws.toolkits.jetbrains.ui.EnvironmentVariablesTextField
import software.aws.toolkits.jetbrains.ui.HandlerPanel
import software.aws.toolkits.jetbrains.ui.ResourceSelector
import software.aws.toolkits.jetbrains.ui.ResourceSelector.Companion.builder
import software.aws.toolkits.jetbrains.ui.SliderPanel
import software.aws.toolkits.jetbrains.utils.lambdaTracingConfigIsAvailable
import software.aws.toolkits.jetbrains.utils.ui.validationInfo
import software.aws.toolkits.resources.message
import java.awt.BorderLayout
import java.util.function.Function
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel

class LambdaConfigPanel(private val project: Project) : JPanel(BorderLayout()) {
    lateinit var handlerPanel: HandlerPanel
        private set
    lateinit var createRole: JButton
        private set
    lateinit var iamRole: ResourceSelector<IamRole>
        private set
    lateinit var runtime: JComboBox<Runtime>
        private set
    lateinit var envVars: EnvironmentVariablesTextField
        private set
    lateinit var memorySlider: SliderPanel
        private set
    lateinit var timeoutSlider: SliderPanel
        private set
    lateinit var handlerLabel: JLabel
        private set
    lateinit var xrayEnabled: JCheckBox
        private set
    private lateinit var content: JPanel

    var runtimeModel = SortedComboBoxModel(compareBy(Comparator.naturalOrder(), Runtime::toString))
        private set
    private var lastSelectedRuntime: Runtime? = null

    init {
        runtimeModel.setAll(Runtime.knownValues())

        runtime.model = runtimeModel
        runtime.addActionListener {
            val selectedRuntime = runtime.selectedItem as? Runtime?
            if (selectedRuntime == lastSelectedRuntime) {
                return@addActionListener
            }
            lastSelectedRuntime = selectedRuntime
            handlerPanel.setRuntime(selectedRuntime)
        }

        createRole.addActionListener {
            val iamRoleDialog = CreateIamRoleDialog(
                project = project,
                iamClient = project.awsClient(),
                parent = createRole,
                defaultAssumeRolePolicyDocument = DEFAULT_ASSUME_ROLE_POLICY,
                defaultPolicyDocument = DEFAULT_POLICY
            )
            if (iamRoleDialog.showAndGet()) {
                iamRoleDialog.iamRole?.let { newRole ->
                    iamRole.reload(forceFetch = true)
                    iamRole.selectedItem = newRole
                }
            }
        }

        content.border = IdeBorderFactory.createTitledBorder(message("lambda.upload.configuration_settings"), false)

        setXrayControlVisibility(lambdaTracingConfigIsAvailable(AwsConnectionManager.getInstance(project).activeRegion))

        add(content, BorderLayout.CENTER)
    }

    private fun createUIComponents() {
        handlerPanel = HandlerPanel(project)
        runtimeModel = SortedComboBoxModel(Comparator.comparing(Function { obj: Runtime -> obj.toString() }, Comparator.naturalOrder()))
        runtime = ComboBox(runtimeModel)
        envVars = EnvironmentVariablesTextField()
        memorySlider = lambdaMemory()
        timeoutSlider = lambdaTimeout()
        iamRole = builder().resource(IamResources.LIST_LAMBDA_ROLES).awsConnection(project).build()
    }

    private fun setXrayControlVisibility(visible: Boolean) {
        xrayEnabled.isVisible = visible
        if (!visible) {
            xrayEnabled.isSelected = false
        }
    }

    fun validatePanel(handlerMustExist: Boolean): ValidationInfo? {
        // Check runtime first, since handler panel needs it to be there
        runtimeModel.selectedItem ?: return runtime.validationInfo(message("lambda.upload_validation.runtime"))

        handlerPanel.validateHandler(handlerMustExist)?.let { return it }

        iamRole.selected() ?: return iamRole.validationInfo(message("lambda.upload_validation.iam_role"))

        return timeoutSlider.validate() ?: memorySlider.validate()
    }
}
