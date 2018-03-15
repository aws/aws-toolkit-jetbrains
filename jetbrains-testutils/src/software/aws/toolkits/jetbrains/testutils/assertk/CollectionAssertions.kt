package software.aws.toolkits.jetbrains.testutils.assertk

import assertk.Assert
import assertk.assertions.support.expected
import assertk.assertions.support.show

fun <T : Collection<*>> Assert<T>.containsOnlyInAnyOrder(vararg elements: Any?) {
    val expected = elements.toMutableList()
    val unexpected = mutableListOf<Any?>()

    actual.forEach {
        if (!expected.remove(it)) {
            unexpected.add(it)
        }
    }

    if (expected.isNotEmpty() || unexpected.isNotEmpty()) {
        val sb = StringBuilder("to contain only: ${show(elements)} in any order but")
        if (expected.isNotEmpty()) {
            sb.append("the following elements were missing: ${show(expected)} ")
        }

        if (unexpected.isNotEmpty()) {
            sb.append("the following elements were not expected: ${show(unexpected)}")
        }
        expected(sb.toString())
    }

}