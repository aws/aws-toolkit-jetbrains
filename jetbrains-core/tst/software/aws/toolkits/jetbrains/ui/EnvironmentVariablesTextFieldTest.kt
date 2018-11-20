// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ui

import com.intellij.execution.util.EnvironmentVariable
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class EnvironmentVariablesTextFieldTest {

    @Test
    fun protectedVars() {
        val field = EnvironmentVariablesTextField()
        field.protectVariables(listOf("A", "B"))
        field.envVars = mapOf("xx" to "1", "yy" to "2")

        val shownInTable: List<EnvironmentVariable> = field.convertToVariables()
        assertThat(shownInTable).hasSize(4)
        assertThat(shownInTable.asKeyValueMap())
                .isEqualTo(mapOf("A" to "", "B" to "", "xx" to "1", "yy" to "2"))

        val a = shownInTable.require("A")
        val b = shownInTable.require("B")

        assertThat(a.nameIsWriteable).isFalse()
        assertThat(b.nameIsWriteable).isFalse()

        // can't be removed
        assertThat(a.isPredefined).isTrue()
        assertThat(b.isPredefined).isTrue()

        assertThat(shownInTable.require("xx").nameIsWriteable).isTrue()
        assertThat(shownInTable.require("yy").isPredefined).isFalse()
    }

    @Test
    fun protectedVarsWithData() {
        val field = EnvironmentVariablesTextField()
        field.protectVariables(listOf("A", "B"))
        field.envVars = mapOf("X" to "xx", "B" to "bb")

        val shownInTable = field.convertToVariables()
        assertThat(shownInTable).hasSize(3)
        assertThat(shownInTable.asKeyValueMap())
                .isEqualTo(mapOf("A" to "", "B" to "bb", "X" to "xx"))
    }

    @Test
    fun thatEmptyProtectedVarsExcludedFromStringify() {
        val field = EnvironmentVariablesTextField()

        field.protectVariables(listOf("AA", "BB", "CC"))
        field.envVars = mapOf("AA" to "aa", "CC" to "", "XX" to "xx")

        assertThat(field.text).isEqualTo("AA=aa;XX=xx")
    }

    @Test
    fun thatStringifyDoesNotDependOnCallOrder() {
        val protectedNames = listOf("AA", "BB", "CC")
        val data = mapOf("AA" to "aa", "CC" to "", "XX" to "xx")

        val protectThanSet = EnvironmentVariablesTextField()
        protectThanSet.protectVariables(protectedNames)
        protectThanSet.envVars = data

        val setThenProtect = EnvironmentVariablesTextField()
        setThenProtect.envVars = data
        setThenProtect.protectVariables(protectedNames)
    }

    @Test
    fun testAfterEditing() {
        val protectedNames = listOf("<clear>", "<edit>", "<empty>")
        val data = mapOf(
                "<clear>" to "AA",
                "<edit>" to "AA",
                "<unprotected-edit>" to "AA",
                "<unprotected-remove>" to "AA",
                "<unprotected-clear>" to "AA"
        )

        val field = EnvironmentVariablesTextField()
        field.envVars = data
        field.protectVariables(protectedNames)

        val vars = field.convertToVariables()

        vars.require("<clear>").value = ""
        vars.require("<edit>").value = "BB"

        vars.require("<unprotected-edit>").value = "BB"
        vars.require("<unprotected-clear>").value = ""

        field.acceptEditedVariablesForTesting(vars.filter { it -> it.name != "<unprotected-remove>" })

        assertThat(field.text).doesNotContain("<clear>=") // cleared protected values considered as kinda removed
        assertThat(field.text).contains("<unprotected-clear>=")

        assertThat(field.text).isEqualTo("<edit>=BB;<unprotected-edit>=BB;<unprotected-clear>=")
        assertThat(field.envVars).isEqualTo(mapOf(
                "<edit>" to "BB",
                "<unprotected-edit>" to "BB",
                "<unprotected-clear>" to ""))
    }

    companion object {
        private fun List<EnvironmentVariable>.asKeyValueMap(): Map<String, String> =
                this.associate { it -> it.name to it.value }

        private fun List<EnvironmentVariable>.require(name: String): EnvironmentVariable =
                this.first { it -> it.name == name }
    }
}