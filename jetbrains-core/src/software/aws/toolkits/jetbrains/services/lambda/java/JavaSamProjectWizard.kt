// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.java

import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.logWhenNull
import software.aws.toolkits.jetbrains.services.lambda.BuiltInRuntimeGroups
import software.aws.toolkits.jetbrains.services.lambda.sam.SamSchemaDownloadPostCreationAction
import software.aws.toolkits.jetbrains.services.lambda.wizard.IntelliJSdkSelectionPanel
import software.aws.toolkits.jetbrains.services.lambda.wizard.SamNewProjectSettings
import software.aws.toolkits.jetbrains.services.lambda.wizard.SamProjectGenerator
import software.aws.toolkits.jetbrains.services.lambda.wizard.SamProjectTemplate
import software.aws.toolkits.jetbrains.services.lambda.wizard.SamProjectWizard
import software.aws.toolkits.jetbrains.services.lambda.wizard.SchemaResourceSelectorSelectionPanel
import software.aws.toolkits.jetbrains.services.lambda.wizard.SchemaSelectionPanel
import software.aws.toolkits.jetbrains.services.lambda.wizard.SdkSelectionPanel
import software.aws.toolkits.jetbrains.services.lambda.wizard.TemplateParameters
import software.aws.toolkits.jetbrains.services.lambda.wizard.TemplateParameters.AppBasedTemplate
import software.aws.toolkits.jetbrains.services.schemas.SchemaCodeLangs
import software.aws.toolkits.resources.message
import java.nio.file.Paths

class JavaSamProjectWizard : SamProjectWizard {
    override fun createSchemaSelectionPanel(generator: SamProjectGenerator): SchemaSelectionPanel =
        SchemaResourceSelectorSelectionPanel(generator.builder, generator.defaultSourceCreatingProject)

    override fun createSdkSelectionPanel(generator: SamProjectGenerator): SdkSelectionPanel =
        IntelliJSdkSelectionPanel(generator.builder, BuiltInRuntimeGroups.Java)

    override fun listTemplates(): Collection<SamProjectTemplate> = listOf(
        SamHelloWorldMaven(),
        SamHelloWorldGradle(),
        SamEventBridgeHelloWorldMaven(),
        SamEventBridgeHelloWorldGradle(),
        SamEventBridgeStarterAppMaven(),
        SamEventBridgeStarterAppGradle()
    )
}

abstract class JavaSamProjectTemplate : SamProjectTemplate() {
    override fun supportedRuntimes() = setOf(Runtime.JAVA8, Runtime.JAVA8_AL2, Runtime.JAVA11)

    // Helper method to locate the build file, such as pom.xml in the project content root.
    protected fun locateBuildFile(contentRoot: VirtualFile, buildFileName: String): VirtualFile? {
        val contentRootFile = VfsUtil.virtualToIoFile(contentRoot)
        val baseSearchPath = contentRootFile.absolutePath

        val buildFile = LOG.logWhenNull("Failed to locate $buildFileName under $baseSearchPath") {
            FileUtil.fileTraverser(contentRootFile).bfsTraversal().first { it.name == buildFileName }
        }

        return buildFile?.let {
            LOG.logWhenNull("Failed to convert $it to VirtualFile") {
                LocalFileSystem.getInstance().refreshAndFindFileByIoFile(it)
            }
        }
    }

    private companion object {
        val LOG = getLogger<SamHelloWorldMaven>()
    }
}

abstract class JavaGradleSamProjectTemplate : JavaSamProjectTemplate() {
    override fun postCreationAction(
        settings: SamNewProjectSettings,
        contentRoot: VirtualFile,
        rootModel: ModifiableRootModel,
        sourceCreatingProject: Project,
        indicator: ProgressIndicator
    ) {
        super.postCreationAction(settings, contentRoot, rootModel, sourceCreatingProject, indicator)
        val buildFile = locateBuildFile(contentRoot, "build.gradle") ?: return

        val gradleProjectSettings = GradleProjectSettings().apply {
            withQualifiedModuleNames()
            externalProjectPath = buildFile.path
        }

        val externalSystemSettings = ExternalSystemApiUtil.getSettings(rootModel.project, GradleConstants.SYSTEM_ID)
        externalSystemSettings.setLinkedProjectsSettings(setOf(gradleProjectSettings))

        val importSpecBuilder = ImportSpecBuilder(rootModel.project, GradleConstants.SYSTEM_ID)
            .forceWhenUptodate()
            // replaces useDefaultCallback which is removed in 2020.3
            // FIX_WHEN_MIN_IS_202 remove this callback (since we don't use it)
            .callback(null)
            .use(ProgressExecutionMode.IN_BACKGROUND_ASYNC)

        ExternalSystemUtil.refreshProjects(importSpecBuilder)
    }
}

class SamHelloWorldMaven : JavaSamProjectTemplate() {
    override fun getName() = message("sam.init.template.hello_world_maven.name")

    override fun getDescription() = message("sam.init.template.hello_world.description")

    override fun templateParameters(): TemplateParameters = AppBasedTemplate("hello-world", "maven")

    override fun postCreationAction(
        settings: SamNewProjectSettings,
        contentRoot: VirtualFile,
        rootModel: ModifiableRootModel,
        sourceCreatingProject: Project,
        indicator: ProgressIndicator
    ) {
        super.postCreationAction(settings, contentRoot, rootModel, sourceCreatingProject, indicator)
        val pomFile = locateBuildFile(contentRoot, "pom.xml") ?: return
        val projectsManager = MavenProjectsManager.getInstance(rootModel.project)
        projectsManager.addManagedFilesOrUnignore(listOf(pomFile))
    }
}

class SamHelloWorldGradle : JavaGradleSamProjectTemplate() {
    override fun getName() = message("sam.init.template.hello_world_gradle.name")

    override fun getDescription() = message("sam.init.template.hello_world.description")

    override fun templateParameters(): TemplateParameters = AppBasedTemplate("hello-world", "gradle")

    override fun postCreationAction(
        settings: SamNewProjectSettings,
        contentRoot: VirtualFile,
        rootModel: ModifiableRootModel,
        sourceCreatingProject: Project,
        indicator: ProgressIndicator
    ) {
        val buildFile = locateBuildFile(contentRoot, "build.gradle") ?: return

        val gradleProjectSettings = GradleProjectSettings().apply {
            withQualifiedModuleNames()
            externalProjectPath = buildFile.path
        }

        val externalSystemSettings = ExternalSystemApiUtil.getSettings(rootModel.project, GradleConstants.SYSTEM_ID)
        externalSystemSettings.setLinkedProjectsSettings(setOf(gradleProjectSettings))

        super.postCreationAction(settings, contentRoot, rootModel, sourceCreatingProject, indicator)
    }
}

class SamEventBridgeStarterAppGradle : JavaGradleSamProjectTemplate() {
    override fun getName() = message("sam.init.template.eventBridge_starterApp_gradle.name")

    override fun getDescription() = message("sam.init.template.eventBridge_starterApp.description")

    override fun functionName(): String = "HelloWorldFunction"

    override fun templateParameters(): TemplateParameters = AppBasedTemplate("eventBridge-schema-app", "gradle")

    override fun supportsDynamicSchemas(): Boolean = true

    override fun postCreationAction(
        settings: SamNewProjectSettings,
        contentRoot: VirtualFile,
        rootModel: ModifiableRootModel,
        sourceCreatingProject: Project,
        indicator: ProgressIndicator
    ) {
        settings.schemaParameters?.let {
            val functionRoot = Paths.get(contentRoot.path, functionName())

            SamSchemaDownloadPostCreationAction().downloadCodeIntoWorkspace(
                it,
                contentRoot,
                functionRoot,
                SchemaCodeLangs.JAVA8,
                sourceCreatingProject,
                rootModel.project,
                indicator
            )
        }

        super.postCreationAction(settings, contentRoot, rootModel, sourceCreatingProject, indicator)
    }
}

class SamEventBridgeStarterAppMaven : JavaGradleSamProjectTemplate() {
    override fun getName() = message("sam.init.template.eventBridge_starterApp_maven.name")

    override fun getDescription() = message("sam.init.template.eventBridge_starterApp.description")

    override fun functionName(): String = "HelloWorldFunction"

    override fun templateParameters(): TemplateParameters = AppBasedTemplate("eventBridge-schema-app", "maven")

    override fun supportsDynamicSchemas(): Boolean = true

    override fun postCreationAction(
        settings: SamNewProjectSettings,
        contentRoot: VirtualFile,
        rootModel: ModifiableRootModel,
        sourceCreatingProject: Project,
        indicator: ProgressIndicator
    ) {
        settings.schemaParameters?.let {
            val functionRoot = Paths.get(contentRoot.path, functionName())

            SamSchemaDownloadPostCreationAction().downloadCodeIntoWorkspace(
                it,
                contentRoot,
                functionRoot,
                SchemaCodeLangs.JAVA8,
                sourceCreatingProject,
                rootModel.project,
                indicator
            )
        }

        super.postCreationAction(settings, contentRoot, rootModel, sourceCreatingProject, indicator)
    }
}

class SamEventBridgeHelloWorldGradle : JavaGradleSamProjectTemplate() {
    override fun getName() = message("sam.init.template.eventBridge_helloWorld_gradle.name")

    override fun getDescription() = message("sam.init.template.eventBridge_helloWorld.description")

    override fun templateParameters(): TemplateParameters = AppBasedTemplate("eventBridge-hello-world", "gradle")
}

class SamEventBridgeHelloWorldMaven : JavaGradleSamProjectTemplate() {
    override fun getName() = message("sam.init.template.eventBridge_helloWorld_maven.name")

    override fun getDescription() = message("sam.init.template.eventBridge_helloWorld.description")

    override fun templateParameters(): TemplateParameters = AppBasedTemplate("eventBridge-hello-world", "maven")
}
