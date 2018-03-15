package software.aws.toolkits.jetbrains.testutils.assertk

import assertk.assert
import assertk.assertions.hasMessageContaining
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.catch
import org.junit.Test

class CollectionAssertionsTest {

    @Test
    fun successfulForItemsOutOfOrder() {
        val actual = listOf("foo", "bar")

        assert(actual).containsOnlyInAnyOrder("bar", "foo")
    }

    @Test
    fun successfulForDuplicateItems() {
        val actual = listOf("foo", "foo", "bar")

        assert(actual).containsOnlyInAnyOrder("foo", "bar", "foo")
    }

    @Test
    fun failureMissingItems() {
        val actual = listOf("foo")

        val exception = catch {
            assert(actual).containsOnlyInAnyOrder("foo", "bar")
        }

        assert(exception).isNotNull {
            it.isInstanceOf(AssertionError::class)
            it.hasMessageContaining("elements were missing")
            it.hasMessageContaining("bar")
        }
    }

    @Test
    fun failureUnexpectedItems() {
        val actual = listOf("foo", "bar", "foo")

        val exception = catch {
            assert(actual).containsOnlyInAnyOrder("foo", "bar")
        }

        assert(exception).isNotNull {
            it.isInstanceOf(AssertionError::class)
            it.hasMessageContaining("elements were not expected")
            it.hasMessageContaining("foo")
        }
    }
}