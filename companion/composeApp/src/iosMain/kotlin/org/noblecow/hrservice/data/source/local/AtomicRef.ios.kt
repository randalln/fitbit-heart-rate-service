package org.noblecow.hrservice.data.source.local

import platform.Foundation.NSLock

/**
 * iOS implementation of AtomicRef using NSLock for thread-safe access.
 */
internal actual class AtomicRef<T> actual constructor(initial: T) {
    private var value: T = initial
    private val lock = NSLock()

    actual fun get(): T {
        lock.lock()
        try {
            return value
        } finally {
            lock.unlock()
        }
    }

    actual fun set(newValue: T) {
        lock.lock()
        try {
            value = newValue
        } finally {
            lock.unlock()
        }
    }
}
