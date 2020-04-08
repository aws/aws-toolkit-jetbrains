// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.statistics

import com.intellij.ide.util.PropertiesComponent
import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class JetBrainsPluginStatisticsTest {

    companion object {
        private const val DEFAULT_PLUGIN_VERSION = "aws.toolkit-1.15"
    }

    private var versionOriginal: String? = null
    private var timestampOriginal: String? = null

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Before
    fun storeOriginalProperties() {
        versionOriginal = PropertiesComponent.getInstance().getValue(JetBrainsPluginStatistics.LAST_VERSION_KEY)
        timestampOriginal = PropertiesComponent.getInstance().getValue(JetBrainsPluginStatistics.LAST_REPORT_TIMESTAMP_KEY)
    }

    @After
    fun restoreProperties() {
        setProperties(version = versionOriginal, timestamp = timestampOriginal)
    }

    @Test
    fun missingStatisticsReportTimestamp() {
        setProperties(
            version = DEFAULT_PLUGIN_VERSION,
            timestamp = null
        )

        val isRequired = JetBrainsPluginStatistics().isReportRequired(DEFAULT_PLUGIN_VERSION)
        assertThat(isRequired).isTrue()
    }

    @Test
    fun noReportIfLessThenADayPassed() {
        setProperties(
            version = DEFAULT_PLUGIN_VERSION,
            timestamp = "${System.currentTimeMillis() - TimeUnit.HOURS.toMillis(12)}"
        )

        val isRequired = JetBrainsPluginStatistics().isReportRequired(DEFAULT_PLUGIN_VERSION)
        assertThat(isRequired).isFalse()
    }

    @Test
    fun reportAfterOneDayPassed() {
        setProperties(
            version = DEFAULT_PLUGIN_VERSION,
            timestamp = "${System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24) - 1}"
        )

        val isRequired = JetBrainsPluginStatistics().isReportRequired(DEFAULT_PLUGIN_VERSION)
        assertThat(isRequired).isTrue()
    }

    @Test
    fun reportWhenVersionIsNotSet() {
        setProperties(
            version = null,
            timestamp = System.currentTimeMillis().toString()
        )

        val isRequired = JetBrainsPluginStatistics().isReportRequired(DEFAULT_PLUGIN_VERSION)
        assertThat(isRequired).isTrue()
    }

    @Test
    fun reportDifferentVersion() {
        setProperties(
            version = DEFAULT_PLUGIN_VERSION,
            timestamp = System.currentTimeMillis().toString()
        )

        val isRequired = JetBrainsPluginStatistics().isReportRequired("aws.toolkit-1.16")
        assertThat(isRequired).isTrue()
    }

    private fun setProperties(version: String?, timestamp: String?) {
        PropertiesComponent.getInstance().setValue(JetBrainsPluginStatistics.LAST_VERSION_KEY, version)
        PropertiesComponent.getInstance().setValue(JetBrainsPluginStatistics.LAST_REPORT_TIMESTAMP_KEY, timestamp)
    }
}
