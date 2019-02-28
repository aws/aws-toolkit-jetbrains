// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.java

import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.psi.PsiElement
import com.intellij.util.io.exists
import com.intellij.util.io.inputStream
import com.intellij.util.io.isDirectory
import com.intellij.util.io.isHidden
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.core.utils.createTemporaryZipFile
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.putNextEntry
import software.aws.toolkits.jetbrains.services.lambda.Lambda
import software.aws.toolkits.jetbrains.services.lambda.LambdaPackage
import software.aws.toolkits.jetbrains.services.lambda.LambdaPackager
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.SamTemplateUtils
import software.aws.toolkits.resources.message
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlin.streams.toList

class JavaLambdaPackager : LambdaPackager() {
    // TODO: Remove override when we switch to sam build
    override fun buildLambdaFromTemplate(
        module: Module,
        templateLocation: Path,
        logicalId: String,
        envVars: Map<String, String>
    ): CompletionStage<LambdaPackage> {

        val function = SamTemplateUtils.findFunctionsFromTemplate(
            module.project,
            templateLocation.toFile()
        ).find { it.logicalName == logicalId }
            ?: throw RuntimeConfigurationError(
                message(
                    "lambda.run_configuration.sam.no_such_function",
                    logicalId,
                    templateLocation
                )
            )

        val handler = function.handler()
        val runtime = Runtime.fromValue(function.runtime())
        val element =
            Lambda.findPsiElementsForHandler(module.project, runtime, handler).firstOrNull()
                ?: throw RuntimeConfigurationError(message("lambda.run_configuration.handler_not_found", handler))

        return buildLambda(module, element, handler, runtime, envVars)
    }

    override fun buildLambda(
        module: Module,
        handlerElement: PsiElement,
        handler: String,
        runtime: Runtime,
        envVars: Map<String, String>
    ): CompletionStage<LambdaPackage> {
        val future = CompletableFuture<LambdaPackage>()
        val compilerManager = CompilerManager.getInstance(module.project)
        val compileScope = compilerManager.createModulesCompileScope(arrayOf(module), true, true)

        compilerManager.make(compileScope) { aborted, errors, _, context ->
            if (!aborted && errors == 0) {
                try {
                    val zipContents = mutableSetOf<ZipEntry>()
                    entriesForModule(module, zipContents)
                    val zipFile = createTemporaryZipFile { zip ->
                        zipContents.forEach {
                            it.sourceFile.use { sourceFile ->
                                zip.putNextEntry(
                                    it.pathInZip,
                                    sourceFile
                                )
                            }
                        }
                    }
                    LOG.debug { "Created temporary zip: $zipFile" }
                    future.complete(LambdaPackage(zipFile, zipFile))
                } catch (e: Exception) {
                    future.completeExceptionally(RuntimeException(message("lambda.package.zip_fail"), e))
                }
            } else if (aborted) {
                future.completeExceptionally(RuntimeException(message("lambda.package.compilation_aborted")))
            } else {
                val errorMessages = context.getMessages(CompilerMessageCategory.ERROR).joinToString("\n")
                future.completeExceptionally(
                    RuntimeException(
                        message(
                            "lambda.package.compilation_errors",
                            errorMessages
                        )
                    )
                )
            }
        }
        return future
    }

    private fun entriesForModule(module: Module, entries: MutableSet<ZipEntry>) {
        productionRuntimeEntries(module).forEach {
            when (it) {
                is ModuleOrderEntry -> it.module?.run { entriesForModule(this, entries) }
                is LibraryOrderEntry -> it.library?.run { addLibrary(this, entries) }
            }
            true
        }
        addModuleFiles(module, entries)
    }

    private fun addLibrary(library: Library, entries: MutableSet<ZipEntry>) {
        library.getFiles(OrderRootType.CLASSES).map { Paths.get(it.presentableUrl) }
            .forEach { entries.add(
                ZipEntry(
                    "lib/${it.fileName}",
                    it
                )
            ) }
    }

    private fun addModuleFiles(module: Module, entries: MutableSet<ZipEntry>) {
        productionRuntimeEntries(module)
            .withoutDepModules()
            .withoutLibraries()
            .pathsList.pathList
            .map { Paths.get(it) }
            .filter { it.exists() }
            .flatMap {
                when {
                    it.isDirectory() -> toEntries(it)
                    else -> throw RuntimeException(
                        message(
                            "lambda.package.unhandled_file_type",
                            it
                        )
                    )
                }
            }
            .forEach { entries.add(it) }
    }

    private fun productionRuntimeEntries(module: Module) =
        OrderEnumerator.orderEntries(module).productionOnly().runtimeOnly().withoutSdk()

    private fun toEntries(path: Path): List<ZipEntry> =
        Files.walk(path).use { files ->
            files.filter { !it.isDirectory() && !it.isHidden() && it.exists() }
                .map {
                    ZipEntry(
                        path.relativize(
                            it
                        ).toString().replace('\\', '/'), it
                    )
                }.toList()
        }

    private data class ZipEntry(val pathInZip: String, val sourceFile: InputStream) {
        constructor(pathInZip: String, sourceFile: Path) : this(pathInZip, sourceFile.inputStream())
    }

    companion object {
        val LOG = getLogger<JavaLambdaPackager>()
    }
}