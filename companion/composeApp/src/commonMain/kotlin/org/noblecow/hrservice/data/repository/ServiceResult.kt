package org.noblecow.hrservice.data.repository

/**
 * Represents the result of a service operation.
 * Uses a sealed class hierarchy to provide type-safe error handling without exceptions.
 */
sealed class ServiceResult<out T> {
    /**
     * Operation completed successfully with a result value.
     */
    data class Success<T>(val value: T) : ServiceResult<T>()

    /**
     * Operation failed with a specific error.
     */
    data class Error(val error: ServiceError) : ServiceResult<Nothing>()
}

/**
 * Specific errors that can occur during service operations.
 */
sealed class ServiceError {
    /**
     * Bluetooth-related errors (permission denied, hardware unavailable, etc.)
     */
    data class BluetoothError(
        val reason: String,
        val cause: Throwable? = null
    ) : ServiceError()

    /**
     * Web server errors (port in use, network issues, etc.)
     */
    data class WebServerError(
        val reason: String,
        val cause: Throwable? = null
    ) : ServiceError()

    /**
     * Permission-related errors
     */
    data class PermissionError(
        val permission: String,
        val cause: Throwable? = null
    ) : ServiceError()

    /**
     * Service is already in the requested state
     */
    data class AlreadyInState(val state: String) : ServiceError()

    /**
     * Unknown or unexpected error
     */
    data class UnknownError(
        val message: String,
        val cause: Throwable? = null
    ) : ServiceError()
}

/**
 * Extension to get a user-friendly error message from ServiceError.
 */
fun ServiceError.toMessage(): String = when (this) {
    is ServiceError.BluetoothError -> "Bluetooth error: $reason"
    is ServiceError.WebServerError -> "Server error: $reason"
    is ServiceError.PermissionError -> "Permission required: $permission"
    is ServiceError.AlreadyInState -> "Services already $state"
    is ServiceError.UnknownError -> "Unexpected error: $message"
}

/**
 * Extension to check if result is successful.
 */
fun <T> ServiceResult<T>.isSuccess(): Boolean = this is ServiceResult.Success

/**
 * Extension to check if result is an error.
 */
fun <T> ServiceResult<T>.isError(): Boolean = this is ServiceResult.Error

/**
 * Extension to get value or null.
 */
fun <T> ServiceResult<T>.getOrNull(): T? = when (this) {
    is ServiceResult.Success -> value
    is ServiceResult.Error -> null
}

/**
 * Extension to get error or null.
 */
fun <T> ServiceResult<T>.errorOrNull(): ServiceError? = when (this) {
    is ServiceResult.Success -> null
    is ServiceResult.Error -> error
}
