package org.aionlink.core

import org.junit.Assert.*
import org.junit.Test

class DiagnosticExportTest {
    @Test fun activityStopCannotOverwriteTheCapturedEvidence() {
        val export = DiagnosticExport()
        var transport = "READING_SERVER"
        export.capture { transport }
        transport = "CANCELLED_BY_BACKGROUND"
        assertEquals("READING_SERVER", export.consume())
        assertNull(export.consume())
    }
    @Test fun aNewExportUsesTheNewAttemptRatherThanAnOldCancelledPicker() {
        val export = DiagnosticExport()
        export.capture { "attempt one" }; export.consume()
        export.capture { "attempt two" }
        assertEquals("attempt two", export.consume())
    }
}
