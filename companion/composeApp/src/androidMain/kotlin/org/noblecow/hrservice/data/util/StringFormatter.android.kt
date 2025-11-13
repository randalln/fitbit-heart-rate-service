package org.noblecow.hrservice.data.util

actual fun formatString(template: String, vararg args: Any?): String = template.format(*args)
