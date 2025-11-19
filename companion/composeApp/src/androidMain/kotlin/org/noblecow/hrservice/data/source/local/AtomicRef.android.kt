package org.noblecow.hrservice.data.source.local

import java.util.concurrent.atomic.AtomicReference

/**
 * Android implementation of AtomicRef using java.util.concurrent.atomic.AtomicReference.
 */
internal actual class AtomicRef<T> actual constructor(initial: T) {
    private val atomic = AtomicReference(initial)

    actual fun get(): T = atomic.get()

    actual fun set(value: T) {
        atomic.set(value)
    }
}
