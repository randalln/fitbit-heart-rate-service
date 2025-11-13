package org.noblecow.hrservice.data.util

import dev.zacsweers.metro.Inject
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString as composeGetString

/**
 * Helper class mostly for easy mocking
 */
@Inject
class ResourceHelper {
    suspend fun getString(resource: StringResource): String = composeGetString(resource)
    suspend fun getString(environment: ResourceEnvironment, resource: StringResource): String =
        composeGetString(environment, resource)

    /**
     * Gets a formatted string resource with arguments.
     * Uses String.format to replace placeholders like %s, %d, etc.
     * @param resource The string resource to format
     * @param formatArgs Arguments to format the string with
     * @return The formatted string
     */
    suspend fun getFormattedString(resource: StringResource, vararg formatArgs: Any?): String {
        val template = composeGetString(resource)
        @Suppress("UNCHECKED_CAST", "SpreadOperator")
        return formatString(template, *formatArgs)
    }
}
