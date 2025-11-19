package org.noblecow.hrservice.data.source.local

/**
 * Platform-agnostic atomic reference wrapper for thread-safe access.
 *
 * Provides thread-safe read and write operations for a mutable reference.
 */
internal expect class AtomicRef<T>(initial: T) {
    fun get(): T
    fun set(value: T)
}
