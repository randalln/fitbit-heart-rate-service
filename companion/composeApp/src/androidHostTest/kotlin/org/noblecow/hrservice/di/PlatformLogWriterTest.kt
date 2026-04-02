package org.noblecow.hrservice.di

import org.junit.Assert.assertNotNull
import org.junit.Test

class PlatformLogWriterTest {

    @Test
    fun `getAppLogWriter returns non-null LogWriter`() {
        val logWriter = getAppLogWriter()
        assertNotNull(logWriter)
    }

    @Test
    fun `multiple calls return non-null LogWriters`() {
        val logWriter1 = getAppLogWriter()
        val logWriter2 = getAppLogWriter()

        assertNotNull(logWriter1)
        assertNotNull(logWriter2)
    }
}
