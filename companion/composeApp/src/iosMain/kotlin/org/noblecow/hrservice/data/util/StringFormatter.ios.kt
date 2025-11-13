package org.noblecow.hrservice.data.util

import platform.Foundation.NSString
import platform.Foundation.stringWithFormat

actual fun formatString(template: String, vararg args: Any?): String {
    // Kotlin/Native requires special handling for Objective-C variadic methods
    // We handle common cases explicitly
    @Suppress("UNCHECKED_CAST")
    return when (args.size) {
        0 -> NSString.stringWithFormat(template) as String
        1 -> NSString.stringWithFormat(template, args[0]) as String
        2 -> NSString.stringWithFormat(template, args[0], args[1]) as String
        3 -> NSString.stringWithFormat(template, args[0], args[1], args[2]) as String
        4 -> NSString.stringWithFormat(template, args[0], args[1], args[2], args[3]) as String
        5 -> NSString.stringWithFormat(template, args[0], args[1], args[2], args[3], args[4]) as String
        else -> throw IllegalArgumentException("formatString supports max 5 arguments, got ${args.size}")
    }
}
