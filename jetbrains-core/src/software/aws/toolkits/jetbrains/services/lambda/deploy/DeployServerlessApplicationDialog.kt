// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.deploy

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import software.amazon.awssdk.services.cloudformation.CloudFormationClient
import software.amazon.awssdk.services.s3.S3Client
import software.aws.toolkits.core.s3.regionForBucket
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.jetbrains.services.cloudformation.Parameter
import software.aws.toolkits.jetbrains.services.s3.CreateS3BucketDialog
import software.aws.toolkits.jetbrains.utils.ui.addAndSelectValue
import software.aws.toolkits.jetbrains.utils.ui.populateValues
import software.aws.toolkits.jetbrains.utils.ui.selected
import software.aws.toolkits.resources.message
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JComponent

class DeployServerlessApplicationDialog(
    private val project: Project,
    private val parameters: Sequence<Parameter>
) : DialogWrapper(project) {

    private val view = DeployServerlessApplicationForm()
    private val validator = DeploySamApplicationValidator()
    private val s3Client: S3Client = project.awsClient()

    private val regionProvider = AwsRegionProvider.getInstance()

    private val deployAction = DeployServerlessApplicationAction()

    init {
        super.init()

        title = message("serverless.application.deploy.title")

        view.content.preferredSize = Dimension(800, 600)

        view.withTemplateParameters(parameters.toList())

        view.region.setRegions(regionProvider.regions().values.toMutableList())
        view.btnCreateS3Bucket.isEnabled = view.region.selectedRegion != null

        view.s3Bucket.populateValues {
            emptyList()
        }

        view.stacks.addItem(getStackPlaceholderSelectRegion())

        // S3 selector only shows buckets for region of interest
        view.region.addActionListener {
            view.btnCreateS3Bucket.isEnabled = view.region.selectedRegion != null
            val activeRegionId = view.region.selectedRegion?.id

            view.s3Bucket.populateValues {
                if (!activeRegionId.isNullOrEmpty()) {
                    s3Client.listBuckets().buckets()
                            .asSequence()
                            .mapNotNull { it?.name() }
                            .filter { s3Client.regionForBucket(it) == activeRegionId }
                            .sortedWith(String.CASE_INSENSITIVE_ORDER)
                            .toList()
                } else {
                    emptyList()
                }
            }

            // Show the stacks that exist in this region
            if (view.region.selectedRegion != null) {
                val cloudFormationClient: CloudFormationClient = project.awsClient(view.region.selectedRegion!!)

                val stacks = ArrayList<String>()
                stacks.add(getStackSelectionTextForCreateStack())

                // Consider adding a horizontal bar into the combo box between the fixed (above) and variable (below) entries

                stacks.addAll(
                        cloudFormationClient.describeStacks().stacks()
                                .asSequence()
                                .mapNotNull { it?.stackName() }
                                .sortedWith(String.CASE_INSENSITIVE_ORDER)
                                .toList()
                )

                view.stacks.populateValues {
                    stacks
                }
            }
        }

        view.stacks.addActionListener {
            val showNewStackControls = view.stacks.selected() == getStackSelectionTextForCreateStack()
            view.newStackNameLabel.isVisible = showNewStackControls
            view.newStackName.isVisible = showNewStackControls
        }

        view.btnCreateS3Bucket.addActionListener {
            // Ensure bucket creation takes place on the currently selected region
            val currentRegionS3Client: S3Client = project.awsClient(view.region.selectedRegion!!)

            val bucketDialog = CreateS3BucketDialog(
                    project = project,
                    s3Client = currentRegionS3Client,
                    parent = view.content
            )

            if (bucketDialog.showAndGet()) {
                bucketDialog.bucketName().let { newBucket -> view.s3Bucket.addAndSelectValue { newBucket } }
            }
        }
    }

    override fun createCenterPanel(): JComponent? = view.content

    override fun getPreferredFocusedComponent(): JComponent? = view.region

    override fun doValidate(): ValidationInfo? {
        val validateSettings = validator.validateSettings(view)
        okAction.isEnabled = validateSettings == null // We can call update settings, but not deploy lambda

        return validateSettings
    }

    override fun getOKAction(): Action = deployAction

    override fun createActions(): Array<Action> {
        val actions = mutableListOf<Action>()
        actions.add(okAction)
        actions.add(cancelAction)

        return actions.toTypedArray()
    }

    override fun doOKAction() {
        // Do nothing, close logic is handled separately
    }

    private fun deployServerlessApplication() {
        if (okAction.isEnabled) {

            // TODO : Iterate through the template, publishing functions to AWS (https://github.com/aws/aws-toolkit-jetbrains/issues/395)
            Messages.showWarningDialog(
                    project,
                    "SAM Deployment is coming soon",
                    "Not Implemented"
            )

            close(OK_EXIT_CODE)
        }
    }

    private inner class DeployServerlessApplicationAction : OkAction() {
        init {
            putValue(Action.NAME, message("serverless.application.deploy.action.name"))
            putValue(Action.SHORT_DESCRIPTION, message("serverless.application.deploy.action.description"))
        }

        override fun doAction(e: ActionEvent?) {
            super.doAction(e)

            if (doValidateAll().isNotEmpty()) {
                return
            }

            deployServerlessApplication()

            close(CANCEL_EXIT_CODE)
        }
    }

    companion object {
        fun getStackPlaceholderSelectRegion(): String = message("serverless.application.stack.placeholder.select.region")

        fun getStackSelectionTextForCreateStack(): String = message("serverless.application.stack.selection.create.stack")
    }
}

class DeploySamApplicationValidator {
    fun validateSettings(view: DeployServerlessApplicationForm): ValidationInfo? {

        // Has the user selected a region
        view.region.selectedRegion ?: return ValidationInfo(
                message("serverless.application.deploy.validation.region.empty"),
                view.region
        )

        // Has the user selected a stack
        val selectedStackName = view.stacks.selected()
                ?: DeployServerlessApplicationDialog.getStackPlaceholderSelectRegion()
        if (selectedStackName == DeployServerlessApplicationDialog.getStackPlaceholderSelectRegion()) {
            return ValidationInfo(
                    message("serverless.application.deploy.validation.stack.missing"),
                    view.stacks
            )
        } else if (selectedStackName == DeployServerlessApplicationDialog.getStackSelectionTextForCreateStack()) {
            if (view.newStackName.text.isNullOrEmpty()) {
                return ValidationInfo(
                        message("serverless.application.deploy.validation.new.stack.name.missing"),
                        view.newStackName
                )
            }

            // Validate stack name
            validateStackName(view.newStackName.text)?.run {
                return@validateSettings ValidationInfo(this, view.newStackName)
            }
        }

        // Are any Template Parameters missing
        val parametersValidation = validateParameters(view)
        if (parametersValidation != null) {
            return parametersValidation
        }

        // Has the user selected a bucket
        view.s3Bucket.selected() ?: return ValidationInfo(
                message("serverless.application.deploy.validation.s3.bucket.empty"),
                view.s3Bucket
        )

        return null
    }

    private fun validateParameters(view: DeployServerlessApplicationForm): ValidationInfo? {
        val parameters = view.templateParameters

        val unsetParameters = parameters.entries
                .filter { it.value.isNullOrBlank() }
                .map { it.key }
                .toList()

        if (unsetParameters.any()) {
            return ValidationInfo(
                    message("serverless.application.deploy.validation.template.values.missing", unsetParameters.joinToString(", ")),
                    view.getTemplateEditorComponent()
            )
        }

        return null
    }

    private fun validateStackName(name: String): String? {
        if (!STACK_NAME_PATTERN.matches(name)) {
            return message("serverless.application.deploy.validation.new.stack.name.invalid")
        }
        if (name.length > MAX_STACK_NAME_LENGTH) {
            return message("serverless.application.deploy.validation.new.stack.name.too.long", MAX_STACK_NAME_LENGTH)
        }
        return null
    }

    companion object {
        // https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-using-console-create-stack-parameters.html
        //  A stack name can contain only alphanumeric characters (case-sensitive) and hyphens. It must start with an alphabetic character and can't be longer than 128 characters.
        private val STACK_NAME_PATTERN = "[a-zA-Z][a-zA-Z0-9-]*".toRegex()
        const val MAX_STACK_NAME_LENGTH = 128
    }
}