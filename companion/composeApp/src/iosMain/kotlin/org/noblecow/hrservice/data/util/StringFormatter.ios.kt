package org.noblecow.hrservice.data.util

import platform.Foundation.NSString
import platform.Foundation.stringWithFormat

actual fun formatString(template: String, vararg args: Any?): String {
    // Kotlin/Native requires special handling for Objective-C variadic methods
    // We handle common cases explicitly
    return when (args.size) {
        0 -> NSString.stringWithFormat(template)
        1 -> NSString.stringWithFormat(template, args[0])
        2 -> NSString.stringWithFormat(template, args[0], args[1])
        3 -> NSString.stringWithFormat(template, args[0], args[1], args[2])
        4 -> NSString.stringWithFormat(template, args[0], args[1], args[2], args[3])
        5 -> NSString.stringWithFormat(template, args[0], args[1], args[2], args[3], args[4])
        else -> throw IllegalArgumentException("formatString supports max 5 arguments, got ${args.size}")
    }
}
