// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ultimate.services.lambda.nodejs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiFile
import software.aws.toolkits.core.utils.createTemporaryZipFile
import software.aws.toolkits.core.utils.putNextEntry
import software.aws.toolkits.jetbrains.services.lambda.LambdaPackage
import software.aws.toolkits.jetbrains.services.lambda.LambdaPackager
import software.aws.toolkits.jetbrains.utils.filesystem.walkFiles
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class NodeJSLambdaPackager : LambdaPackager {
    override fun createPackage(module: Module, file: PsiFile): CompletionStage<LambdaPackage> {
        val future = CompletableFuture<LambdaPackage>()
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val excludedRoots = mutableSetOf(*module.rootManager.excludeRoots)

                val mappings = mutableMapOf<String, String>()
                val packagedFile = createTemporaryZipFile { zip ->
                    /* TODO instead of walking through source roots (which were set up manually)
                            we should pick up the proper package.json or get source from CodeUri field
                    */
                    ModuleRootManager.getInstance(module).sourceRoots.forEach { sourceRoot ->
                        sourceRoot.walkFiles(excludedRoots) { file ->
                            mappings[sourceRoot.path] = "/"
                            VfsUtilCore.getRelativeLocation(file, sourceRoot)?.let { relativeLocation ->
                                file.inputStream.use { fileContents ->
                                    zip.putNextEntry(relativeLocation, fileContents)
                                }
                            }
                        }
                    }
                }
                future.complete(LambdaPackage(packagedFile, mappings))
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        return future
    }
}