package software.aws.toolkits.jetbrains.utils

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class MRUList<T>(private val maxSize: Int) {
    private val lock = ReentrantReadWriteLock()
    private val internalList = mutableListOf<T>()

    fun add(element: T) {
        lock.write {
            internalList.remove(element)
            internalList.add(0, element)
            trimToSize()
        }
    }

    fun elements(): List<T> {
        return lock.read {
            internalList.toList()
        }
    }

    private fun trimToSize() {
        while (internalList.size > maxSize) {
            internalList.removeAt(internalList.size - 1)
        }
    }
}