// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.wizard

import com.intellij.execution.RunManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.core.utils.AttributeBag
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.services.lambda.RuntimeGroup
import software.aws.toolkits.jetbrains.services.lambda.RuntimeGroupExtensionPointObject
import software.aws.toolkits.jetbrains.services.lambda.execution.local.LocalLambdaRunConfiguration
import software.aws.toolkits.jetbrains.services.lambda.execution.local.LocalLambdaRunConfigurationProducer
import software.aws.toolkits.jetbrains.services.lambda.sam.SamCommon
import software.aws.toolkits.jetbrains.services.lambda.sam.SamTemplateUtils
import software.aws.toolkits.jetbrains.services.schemas.SchemaTemplateParameters
import software.aws.toolkits.jetbrains.services.schemas.resources.SchemasResources.AWS_EVENTS_REGISTRY
import software.aws.toolkits.telemetry.SamTelemetry
import software.aws.toolkits.telemetry.Runtime as TelemetryRuntime

/**
 * Used to manage SAM project information for different [RuntimeGroup]s
 */
interface SamProjectWizard {

    /**
     * Return a collection of templates supported by the [RuntimeGroup]
     */
    fun listTemplates(): Collection<SamProjectTemplate>

    /**
     * Return an instance of UI section for selecting SDK for the [RuntimeGroup]
     */
    fun createSdkSelectionPanel(generator: SamProjectGenerator?): SdkSelector?

    companion object : RuntimeGroupExtensionPointObject<SamProjectWizard>(ExtensionPointName("aws.toolkit.lambda.sam.projectWizard"))
}

data class SamNewProjectSettings(
    val runtime: Runtime,
    val template: SamProjectTemplate,
    val attributeBag: AttributeBag = AttributeBag()
)

sealed class TemplateParameters {
    data class LocationBasedTemplate(val location: String) : TemplateParameters()
    data class AppBasedTemplate(val appTemplate: String, val dependencyManager: String) : TemplateParameters()
}

abstract class SamProjectTemplate {
    abstract fun getName(): String

    open fun getDescription(): String? = null

    open fun functionName(): String = "HelloWorldFunction"

    override fun toString() = getName()

    open fun postCreationAction(
        settings: SamNewProjectSettings,
        contentRoot: VirtualFile,
        rootModel: ModifiableRootModel,
        indicator: ProgressIndicator
    ) {
        SamCommon.excludeSamDirectory(contentRoot, rootModel)
        val project = rootModel.project
        openReadmeFile(contentRoot, project)
        createRunConfigurations(contentRoot, project)
    }

    private fun openReadmeFile(contentRoot: VirtualFile, project: Project) {
        VfsUtil.findRelativeFile(contentRoot, "README.md")?.let { readme ->
            // it's only available since the first non-EAP version of intellij, so it is fine
            readme.putUserData(TextEditorWithPreview.DEFAULT_LAYOUT_FOR_FILE, TextEditorWithPreview.Layout.SHOW_PREVIEW)

            val fileEditorManager = FileEditorManager.getInstance(project)
            fileEditorManager.openTextEditor(OpenFileDescriptor(project, readme), true) ?: LOG.warn { "Failed to open README.md" }
        }
    }

    private fun createRunConfigurations(contentRoot: VirtualFile, project: Project) {
        val template = SamCommon.getTemplateFromDirectory(contentRoot) ?: return

        val factory = LocalLambdaRunConfigurationProducer.getFactory()
        val runManager = RunManager.getInstance(project)
        SamTemplateUtils.findFunctionsFromTemplate(project, template).forEach {
            val runConfigurationAndSettings = runManager.createConfiguration(it.logicalName, factory)
            val runConfiguration = runConfigurationAndSettings.configuration as LocalLambdaRunConfiguration
            runConfiguration.useTemplate(template.path, it.logicalName)
            runConfiguration.setGeneratedName()
            runManager.addConfiguration(runConfigurationAndSettings)
            if (runManager.selectedConfiguration == null) {
                runManager.selectedConfiguration = runConfigurationAndSettings
            }
        }
    }

    fun build(project: Project?, name: String, runtime: Runtime, schemaParameters: SchemaTemplateParameters?, outputDir: VirtualFile) {
        var success = true
        try {
            doBuild(name, runtime, schemaParameters, outputDir)
        } catch (e: Throwable) {
            success = false
            throw e
        } finally {
            SamTelemetry.init(
                project = project,
                name = getName(),
                success = success,
                runtime = TelemetryRuntime.from(runtime.toString()),
                version = SamCommon.getVersionString(),
                templateName = this.javaClass.simpleName,
                eventBridgeSchema = if (schemaParameters?.schema?.registryName == AWS_EVENTS_REGISTRY) schemaParameters.schema.name else null
            )
        }
    }

    private fun doBuild(name: String, runtime: Runtime, schemaParameters: SchemaTemplateParameters?, outputDir: VirtualFile) {
        SamInitRunner.execute(
            name,
            outputDir,
            runtime,
            templateParameters(),
            if (supportsDynamicSchemas()) schemaParameters else null
        )
    }

    protected abstract fun templateParameters(): TemplateParameters

    abstract fun supportedRuntimes(): Set<Runtime>

    // Gradual opt-in for Schema support on a template by-template basis.
    // All SAM templates should support schema selection, but for launch include only EventBridge for most optimal customer experience
    open fun supportsDynamicSchemas(): Boolean = false

    companion object {
        private val LOG = getLogger<SamProjectTemplate>()

        // Dont cache this since it is not compatible in a dynamic plugin world / waste memory if no longer needed
        fun supportedTemplates() = SamProjectWizard.supportedRuntimeGroups().flatMap {
            SamProjectWizard.getInstance(it).listTemplates()
        }
    }
}
