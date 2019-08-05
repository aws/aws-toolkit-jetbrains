// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ui

import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.runInEdtAndWait
import com.nhaarman.mockitokotlin2.mock
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.core.MockResourceCache
import software.aws.toolkits.jetbrains.core.Resource
import software.aws.toolkits.resources.message
import java.util.concurrent.CompletableFuture

class ResourceSelectorTest {
    @Rule
    @JvmField
    val projectRule = ProjectRule()

    private val mockResource = mock<Resource.Cached<List<String>>>()

    private val mockResourceCache = MockResourceCache.getInstance(projectRule.project)

    @Test
    fun canSpecifyADefaultItem() {
        val comboBox = ResourceSelector(projectRule.project, mockResource)
        mockResourceCache.addEntry(mockResource, listOf("foo", "bar", "baz"))

        comboBox.model.selectedItem = "foo"
        comboBox.load(default = "bar")

        waitForPopulationComplete(comboBox, 3)
        assertThat(comboBox.selectedItem).isEqualTo("bar")
    }

    @Test
    fun previouslySelectedIsRetainedIfNoDefault() {
        val comboBox = ResourceSelector(projectRule.project, mockResource)
        mockResourceCache.addEntry(mockResource, listOf("foo", "bar", "baz"))

        comboBox.load()
        comboBox.model.selectedItem = "bar"
        comboBox.load()

        waitForPopulationComplete(comboBox, 3)
        assertThat(comboBox.selectedItem).isEqualTo("bar")
    }

    @Test
    fun canSpecifyADefaultItemMatcher() {
        val comboBox = ResourceSelector(projectRule.project, mockResource)
        mockResourceCache.addEntry(mockResource, listOf("foo", "bar", "baz"))

        comboBox.load(defaultMatcher = { it.endsWith("z") })

        waitForPopulationComplete(comboBox, 3)
        assertThat(comboBox.selectedItem).isEqualTo("baz")
    }

//    @Test
//    fun comboBoxPopulation_overrideDefaultSelected() {
//        mockResourceCache.addEntry(mockResource, listOf("foo", "bar", "baz"))
//
//        comboBox.model.selectedItem = "foo"
//        comboBox.load(default = "bar", forceSelectDefault = false)
//
//        waitForPopulationComplete(comboBox, 3)
//        assertThat(comboBox.selectedItem).isEqualTo("foo")
//    }

//    @Test
//    fun comboBoxPopulation_useDefaultSelectedWhenPreviouslySelectedIsNull() {
//        val items = listOf("foo", "bar", "baz")
//
//        comboBox.model.selectedItem = null
//        comboBox.populateValues(default = "bar", forceSelectDefault = false) { items }
//
//        waitForPopulationComplete(comboBox, items.size)
//        assertThat(comboBox.selectedItem).isEqualTo("bar")
//    }

//    @Test
//    fun comboBoxPopulation_notUpdateState() {
//        val items = listOf("foo", "bar", "baz")
//
//        comboBox.isEnabled = false
//        comboBox.populateValues(updateStatus = false) { items }
//
//        waitForPopulationComplete(comboBox, items.size)
//        assertThat(comboBox.isEnabled).isEqualTo(false)
//    }

    @Test
    fun comboBoxIsDisabledWhileEntriesAreLoading() {
        val comboBox = ResourceSelector(projectRule.project, mockResource)
        val future = CompletableFuture<List<String>>()
        mockResourceCache.addEntry(mockResource,  future)

        assertThat(comboBox.selectedItem).isNull()

        comboBox.load()
        runInEdtAndWait {
            assertThat(comboBox.isEnabled).isFalse()
            assertThat(comboBox.selectedItem).isEqualTo(message("loading_resource.loading"))
        }

        future.complete(listOf("foo", "bar", "baz"))
        runInEdtAndWait {
            assertThat(comboBox.isEnabled).isTrue()
        }
    }

//    @Test
//    fun comboBoxPopulation_updateStateToTrueWhenItemsAreNotEmpty() {
//        val items = listOf("foo", "bar", "baz")
//
//        comboBox.isEnabled = false
//        comboBox.populateValues(updateStatus = true) { items }
//
//        waitForPopulationComplete(comboBox, items.size)
//        assertThat(comboBox.isEnabled).isEqualTo(true)
//    }
//
//    @Test
//    fun comboBoxPopulation_updateStateToFalseWhenItemsAreEmpty() {
//
//        arrayOf("foo", "bar").forEach { comboBox.addItem(it) }
//
//        comboBox.isEnabled = true
//        comboBox.populateValues(updateStatus = true) { listOf() }
//
//        waitForPopulationComplete(comboBox, 0)
//        assertThat(comboBox.isEnabled).isEqualTo(false)
//    }
//
//    @Test
//    fun comboBoxPopulation_loadingAndFailed() {
//
//        arrayOf("foo", "bar").forEach { comboBox.addItem(it) }
//
//        val exception = Exception("Failed")
//        comboBox.populateValues { throw exception }
//
//        waitForPopulationComplete(comboBox, 0)
//        assertThat(comboBox.loadingException).isEqualTo(exception)
//    }

    // Wait for the combo box population complete by detecting the item count
    private fun <T> waitForPopulationComplete(comboBox: ComboBox<T>, count: Int) {
        while (comboBox.itemCount != count) {
            Thread.sleep(100)
        }
    }
}