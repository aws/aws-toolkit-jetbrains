// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.icons

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.IconPathPatcher
import com.intellij.ui.LayeredIcon
import com.intellij.util.ReflectionUtil
import icons.AwsIcons
import javax.swing.Icon

/**
 * Icons Patcher for backend icons. Rider backend do not have access to fronted icons (e.g. LambdaFunction.svg, new.svg).
 * Use this class to set frontend icons for gutter mark popup menu that is come from backend in Rider.
 */
internal class RiderAwsIconsPatcher : IconPathPatcher() {

    companion object {
        fun install() = myInstallPatcher

        private val myInstallPatcher: Unit by lazy {
            IconLoader.installPathPatcher(RiderAwsIconsPatcher())
        }

        private fun path(icon: Icon): String {
            val iconToProcess = (icon as? LayeredIcon)?.getIcon(0) ?: icon

            if (iconToProcess is IconLoader.CachedImageIcon) {
                return iconToProcess.originalPath
                    ?: throw RuntimeException("Unable to get original path for icon: ${iconToProcess::class.java.canonicalName}")
            }

            return ReflectionUtil.getField(iconToProcess::class.java, iconToProcess, String::class.java, "myOriginalPath")
                ?: throw RuntimeException("myOriginal path wasn't found in ${iconToProcess.javaClass.simpleName}")
        }
    }

    override fun patchPath(path: String?, classLoader: ClassLoader?): String? =
        if (path != null) myIconsOverrideMap[path] else null

    override fun getContextClassLoader(path: String?, originalClassLoader: ClassLoader?): ClassLoader? {
        if (path != null && myIconsOverrideMap.containsKey(path))
            return javaClass.classLoader

        return originalClassLoader
    }

    private val myIconsOverrideMap = mapOf(
        "/resharper/LambdaRunMarkers/Lambda.svg" to path(AwsIcons.Resources.LAMBDA_FUNCTION),
        "/resharper/LambdaRunMarkers/CreateNew.svg" to path(AllIcons.Actions.New)
    )
}
