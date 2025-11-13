
package org.noblecow.hrservice.data.util

/**
 * Platform-specific string formatting function.
 * Uses String.format on JVM/Android and NSString formatting on iOS.
 */
expect fun formatString(template: String, vararg args: Any?): String
