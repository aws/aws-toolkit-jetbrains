// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.rds.auth

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.url.template.UrlEditorModel
import com.intellij.testFramework.ProjectRule
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider

class IamAuthWidgetTest {
    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Test
    fun setsRegionFromUrl() {
        val widget = IamAuthWidget()
        val region = AwsRegionProvider.getInstance().allRegions().keys.first()
        val endpointUrl = "jdbc:postgresql://abc.host.$region.rds.amazonaws.com:5432/dev"
        widget.updateFromUrl(mock<UrlEditorModel> { on { url } doReturn endpointUrl })
        assertThat(widget.getRegionFromWidget()).isEqualTo(region)
    }

    @Test
    fun doesNotUnsetRegionInvalidUrl() {
        val widget = IamAuthWidget()
        val region = AwsRegionProvider.getInstance().allRegions().keys.first()
        val endpointUrl = "jdbc:postgresql://abc.host.$region.rds.amazonaws.com:5432/dev"
        widget.updateFromUrl(mock<UrlEditorModel> { on { url } doReturn endpointUrl })
        val badUrl = "jdbc:postgresql://abc.host.1000000%invalidregion.rds.amazonaws.com:5432/dev"
        widget.updateFromUrl(mock<UrlEditorModel> { on { url } doReturn badUrl })
        assertThat(widget.getRegionFromWidget()).isEqualTo(region)
    }

    // Get region out of widget by saving settings
    private fun IamAuthWidget.getRegionFromWidget(): String? {
        val dataSource = mock<LocalDataSource> {
            val map = mutableMapOf<String, String>()
            on { additionalJdbcProperties } doReturn map
        }
        save(dataSource, false)
        return dataSource.additionalJdbcProperties[REGION_ID_PROPERTY]
    }
}
