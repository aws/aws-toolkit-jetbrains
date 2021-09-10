// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import java.time.Duration
import java.util.concurrent.CountDownLatch
import javax.swing.DefaultComboBoxModel

class AsyncComboBoxTest {
    @Test
    fun `can populate combobox`() {
        val spy = spy<DefaultComboBoxModel<String>>()
        val sut = AsyncComboBox(comboBoxModel = spy)
        sut.proposeModelUpdate { it.addElement("1") }
        assertThat(sut.blockingGet(Duration.ofSeconds(5))).isEqualTo("1")

        verify(spy).addElement("1")
    }

    @Test
    fun `returns null selection while loading`() {
        val spy = spy<DefaultComboBoxModel<String>>()
        val sut = AsyncComboBox(comboBoxModel = spy)
        val latch = CountDownLatch(1)
        sut.proposeModelUpdate {
            latch.await()
            it.addElement("1")
        }

        assertThat(sut.selectedItem).isNull()

        latch.countDown()
    }

    @Test
    fun `multiple update proposals results in single execution`() {
        val spy = spy<DefaultComboBoxModel<String>>()
        val sut = AsyncComboBox(comboBoxModel = spy)
        sut.proposeModelUpdate { it.addElement("1") }
        sut.proposeModelUpdate { it.addElement("2") }
        sut.proposeModelUpdate { it.addElement("3") }

        assertThat(sut.blockingGet(Duration.ofSeconds(5))).isEqualTo("3")

        verify(spy).addElement("3")
    }
}
