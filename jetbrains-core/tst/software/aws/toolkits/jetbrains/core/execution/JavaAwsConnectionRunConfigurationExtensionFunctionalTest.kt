// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.execution

import com.intellij.compiler.CompilerTestUtil
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.Output
import com.intellij.execution.OutputListener
import com.intellij.execution.RunManager
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.CompilerProjectExtension
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.core.credentials.MockCredentialsManager.Companion.DUMMY_PROVIDER_IDENTIFIER
import software.aws.toolkits.jetbrains.core.region.MockRegionProvider
import software.aws.toolkits.jetbrains.utils.rules.HeavyJavaCodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.utils.rules.addClass
import software.aws.toolkits.jetbrains.utils.rules.addModule
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotNull

class JavaAwsConnectionRunConfigurationExtensionFunctionalTest {

    @Before
    fun setUp() {
        CompilerTestUtil.enableExternalCompiler()
    }

    @After
    fun tearDown() {
        CompilerTestUtil.disableExternalCompiler(projectRule.project)
    }

    @Rule
    @JvmField
    val projectRule = HeavyJavaCodeInsightTestFixtureRule()

    @Test
    fun connectionDetailsAreInjected() {
        val fixture = projectRule.fixture

        val module = fixture.addModule("main")

        val psiClass = fixture.addClass(
            module,
            """
            package com.example;

            public class AnyOldClass {
                public static void main(String[] args) {
                    System.out.println(System.getenv("AWS_REGION"));
                }
            }
            """
        )

        val mockRegion = MockRegionProvider.getInstance().defaultRegion().id
        val runManager = RunManager.getInstance(projectRule.project)
        val configuration = runManager.createConfiguration("test", ApplicationConfigurationType::class.java)
        val runConfiguration = configuration.configuration as ApplicationConfiguration
        runConfiguration.putCopyableUserData(AWS_CONNECTION_RUN_CONFIGURATION_KEY, AwsConnectionRunConfigurationExtensionOptions {
            region = mockRegion
            credential = DUMMY_PROVIDER_IDENTIFIER.id
        })
        runConfiguration.setMainClass(psiClass)

        val executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID)
        assertNotNull(executor)
        val executionEnvironment = ExecutionEnvironmentBuilder.create(executor, runConfiguration).build()

        compileModule(module)

        val executionFuture = CompletableFuture<Output>()
        runInEdt {
            executionEnvironment.runner.execute(executionEnvironment) {
                it.processHandler?.addProcessListener(object : OutputListener() {
                    override fun processTerminated(event: ProcessEvent) {
                        super.processTerminated(event)
                        executionFuture.complete(this.output)
                    }
                })
            }
        }

        assertThat(executionFuture.get(30, TimeUnit.SECONDS).stdout).isEqualToIgnoringWhitespace(mockRegion)
    }

    private fun compileModule(module: Module) {
        setUpCompiler()
        val compileFuture = CompletableFuture<CompileContext>()
        ApplicationManager.getApplication().invokeAndWait {
            CompilerManager.getInstance(module.project).rebuild { aborted, errors, _, context ->
                if (!aborted && errors == 0) {
                    compileFuture.complete(context)
                } else {
                    compileFuture.completeExceptionally(
                        RuntimeException(
                            "Compilation error: ${context.getMessages(CompilerMessageCategory.ERROR).map { it.message }}"
                        )
                    )
                }
            }
        }
        compileFuture.get(30, TimeUnit.SECONDS)
    }

    private fun setUpCompiler() {
        val project = projectRule.project
        val modules = ModuleManager.getInstance(project).modules

        WriteCommandAction.writeCommandAction(project).run<Nothing> {
            val compilerExtension = CompilerProjectExtension.getInstance(project)!!
            compilerExtension.compilerOutputUrl = projectRule.fixture.tempDirFixture.findOrCreateDir("out").url
            val jdkHome = IdeaTestUtil.requireRealJdkHome()
            VfsRootAccess.allowRootAccess(projectRule.fixture.testRootDisposable, jdkHome)
            val jdkHomeDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(jdkHome)!!
            val jdkName = "Real JDK"
            val jdk = SdkConfigurationUtil.setupSdk(emptyArray(), jdkHomeDir, JavaSdk.getInstance(), false, null, jdkName)!!

            ProjectJdkTable.getInstance().addJdk(jdk)
            Disposer.register(
                projectRule.fixture.testRootDisposable,
                Disposable { WriteAction.runAndWait<Nothing> { ProjectJdkTable.getInstance().removeJdk(jdk) } }
            )

            for (module in modules) {
                ModuleRootModificationUtil.setModuleSdk(module, jdk)
            }
        }

        runInEdtAndWait {
            PlatformTestUtil.saveProject(project)
            CompilerTestUtil.saveApplicationSettings()
        }
    }
}
