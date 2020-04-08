// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.statistics

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.HttpRequests
import org.jdom.JDOMException
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.AwsToolkit
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * The editor listener to be able to collect anonymous information about plugin usages in JetBrains marketplace.
 * This code send a statistic information every day while using an IDE with AWS plugin.
 *
 * TODO: Re-write using [software.aws.toolkits.jetbrains.core.statistics.JetBrainsPluginStatisticsProvider]. FIX_WHEN_MIN_IS_201
 */
class JetBrainsPluginStatistics : EditorFactoryListener {

    companion object {
        const val LAST_VERSION_KEY = "${AwsToolkit.PLUGIN_ID}.LAST_VERSION"
        const val LAST_REPORT_TIMESTAMP_KEY = "${AwsToolkit.PLUGIN_ID}.LAST_UPDATE"

        private val logger = getLogger<JetBrainsPluginStatistics>()
        private val lock = Object()

        private val retentionPeriodMillis: Long = TimeUnit.DAYS.toMillis(1)
    }

    override fun editorCreated(event: EditorFactoryEvent) {
        super.editorCreated(event)

        ApplicationManager.getApplication().executeOnPooledThread {
            reportStatistics()
        }
    }

    private fun reportStatistics() {
        AwsToolkit.plugin ?: let {
            logger.error { "Cannot get AWS plugin with ID: '${AwsToolkit.PLUGIN_ID}'." }
            return
        }

        val pluginVersion = AwsToolkit.PLUGIN_VERSION

        if (!isReportRequired(pluginVersion)) return

        val buildNumber = ApplicationInfoEx.getInstanceEx().build.asString()
        val os = URLEncoder.encode("${SystemInfo.OS_NAME} ${SystemInfo.OS_VERSION}", Charsets.UTF_8.name())
        val uid = PermanentInstallationID.get()
        val url = "https://plugins.jetbrains.com/plugins/list" +
            "?pluginId=${AwsToolkit.PLUGIN_ID}" +
            "&build=$buildNumber" +
            "&pluginVersion=$pluginVersion" +
            "&os=$os" +
            "&uuid=$uid"

        try {
            HttpRequests.request(url).connect { request ->
                try {
                    JDOMUtil.load(request.reader)
                } catch (e: JDOMException) {
                    logger.warn { "Error requesting JetBrains statistics update: $e" }
                    return@connect
                }
                logger.info { "Statistic update for plugin with ID '${AwsToolkit.PLUGIN_ID}': $url" }
            }
        } catch (t: Throwable) {
            logger.warn { "Exception while composing JetBrains statistics request: $t" }
        }
    }

    fun isReportRequired(pluginVersion: String): Boolean {
        synchronized(lock) {
            val properties = PropertiesComponent.getInstance()
            val version = properties.getValue(LAST_VERSION_KEY)
            // TODO: Replace with getLong() method. FIX_WHEN_MIN_IS_201
            val lastReportTimestamp = properties.getOrInitLong(LAST_REPORT_TIMESTAMP_KEY, 0L)

            val shouldReport =
                lastReportTimestamp == 0L ||
                    System.currentTimeMillis() - lastReportTimestamp > retentionPeriodMillis ||
                    version == null ||
                    version != pluginVersion

            if (!shouldReport) return false

            properties.setValue(LAST_REPORT_TIMESTAMP_KEY, System.currentTimeMillis().toString())
            properties.setValue(LAST_VERSION_KEY, pluginVersion)

            return true
        }
    }
}
