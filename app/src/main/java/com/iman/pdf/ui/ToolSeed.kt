package com.iman.pdf.ui

import android.net.Uri

/** Carries the PDF that is open in the viewer to a tool screen, so the tool does not ask for the file again. */
object ToolSeed {
    var uri: Uri? = null

    fun take(): Uri? {
        val value = uri
        uri = null
        return value
    }
}
