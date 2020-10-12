// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.java

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
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
import software.aws.toolkits.jetbrains.services.lambda.wizard.AppBasedTemplate
import software.aws.toolkits.jetbrains.services.lambda.wizard.IntelliJSdkSelectionPanel
import software.aws.toolkits.jetbrains.services.lambda.wizard.SamNewProjectSettings
import software.aws.toolkits.jetbrains.services.lambda.wizard.SamProjectTemplate
import software.aws.toolkits.jetbrains.services.lambda.wizard.SamProjectWizard
import software.aws.toolkits.jetbrains.services.lambda.wizard.SdkSelector
import software.aws.toolkits.jetbrains.services.lambda.wizard.TemplateParameters
import software.aws.toolkits.resources.message

class JavaSamProjectWizard : SamProjectWizard {
    override fun createSdkSelectionPanel(projectLocation: TextFieldWithBrowseButton?): SdkSelector? =
        IntelliJSdkSelectionPanel(BuiltInRuntimeGroups.Java)

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

abstract class JavaMavenSamProjectTemplate : JavaSamProjectTemplate() {
    override fun postCreationAction(
        settings: SamNewProjectSettings,
        contentRoot: VirtualFile,
        rootModel: ModifiableRootModel,
        indicator: ProgressIndicator
    ) {
        super.postCreationAction(settings, contentRoot, rootModel, indicator)
        val pomFile = locateBuildFile(contentRoot, "pom.xml") ?: return
        val projectsManager = MavenProjectsManager.getInstance(rootModel.project)
        runInEdt {
            projectsManager.addManagedFilesOrUnignore(listOf(pomFile))
        }
    }
}

abstract class JavaGradleSamProjectTemplate : JavaSamProjectTemplate() {
    override fun postCreationAction(
        settings: SamNewProjectSettings,
        contentRoot: VirtualFile,
        rootModel: ModifiableRootModel,
        indicator: ProgressIndicator
    ) {
        val buildFile = locateBuildFile(contentRoot, "build.gradle") ?: return

        val gradleProjectSettings = GradleProjectSettings().apply {
            withQualifiedModuleNames()
            externalProjectPath = buildFile.path
        }

        val externalSystemSettings = ExternalSystemApiUtil.getSettings(rootModel.project, GradleConstants.SYSTEM_ID)
        externalSystemSettings.setLinkedProjectsSettings(setOf(gradleProjectSettings))

        super.postCreationAction(settings, contentRoot, rootModel, indicator)
    }
}

class SamHelloWorldMaven : JavaMavenSamProjectTemplate() {
    override fun displayName() = message("sam.init.template.hello_world_maven.name")

    override fun description() = message("sam.init.template.hello_world.description")

    override fun templateParameters(projectName: String, runtime: Runtime): TemplateParameters = AppBasedTemplate(
        projectName,
        runtime,
        "hello-world",
        "maven"
    )
}

class SamHelloWorldGradle : JavaGradleSamProjectTemplate() {
    override fun displayName() = message("sam.init.template.hello_world_gradle.name")

    override fun description() = message("sam.init.template.hello_world.description")

    override fun templateParameters(projectName: String, runtime: Runtime): TemplateParameters = AppBasedTemplate(
        projectName,
        runtime,
        "hello-world",
        "gradle"
    )
}

class SamEventBridgeStarterAppGradle : JavaGradleSamProjectTemplate() {
    override fun displayName() = message("sam.init.template.event_bridge_starter_app_gradle.name")

    override fun description() = message("sam.init.template.event_bridge_starter_app.description")

    override fun templateParameters(projectName: String, runtime: Runtime): TemplateParameters = AppBasedTemplate(
        projectName,
        runtime,
        "eventBridge-schema-app",
        "gradle"
    )

    override fun supportsDynamicSchemas(): Boolean = true
}

class SamEventBridgeStarterAppMaven : JavaMavenSamProjectTemplate() {
    override fun displayName() = message("sam.init.template.event_bridge_starter_app_maven.name")

    override fun description() = message("sam.init.template.event_bridge_starter_app.description")

    override fun templateParameters(projectName: String, runtime: Runtime): TemplateParameters = AppBasedTemplate(
        projectName,
        runtime,
        "eventBridge-schema-app",
        "maven"
    )

    override fun supportsDynamicSchemas(): Boolean = true
}

class SamEventBridgeHelloWorldGradle : JavaGradleSamProjectTemplate() {
    override fun displayName() = message("sam.init.template.event_bridge_hello_world_gradle.name")

    override fun description() = message("sam.init.template.event_bridge_hello_world.description")

    override fun templateParameters(projectName: String, runtime: Runtime): TemplateParameters = AppBasedTemplate(
        projectName,
        runtime,
        "eventBridge-hello-world",
        "gradle"
    )
}

class SamEventBridgeHelloWorldMaven : JavaMavenSamProjectTemplate() {
    override fun displayName() = message("sam.init.template.event_bridge_hello_world_maven.name")

    override fun description() = message("sam.init.template.event_bridge_hello_world.description")

    override fun templateParameters(projectName: String, runtime: Runtime): TemplateParameters = AppBasedTemplate(
        projectName,
        runtime,
        "eventBridge-hello-world",
        "maven"
    )
}
