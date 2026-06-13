package org.julsz.smnt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Opens the native Windows "Save As" dialog via PowerShell / Windows Forms.
 * Returns the chosen [File], or null if the user cancelled.
 * The returned path already has the requested extension enforced by the OS dialog.
 */
suspend fun saveFilePicker(title: String, defaultName: String, filter: String): File? =
    withContext(Dispatchers.IO) {
        val script = buildPsScript(
            dialogClass = "SaveFileDialog",
            title       = title,
            filter      = filter,
            extra       = "\$d.FileName = '${defaultName.replace("'", "")}'"
        )
        runPs(script)
    }

/**
 * Opens the native Windows "Open" dialog via PowerShell / Windows Forms.
 * Returns the chosen [File], or null if the user cancelled.
 */
suspend fun openFilePicker(title: String, filter: String): File? =
    withContext(Dispatchers.IO) {
        runPs(buildPsScript(dialogClass = "OpenFileDialog", title = title, filter = filter))
    }

// ─────────────────────────────────────────────────────────────────────────────

private fun buildPsScript(
    dialogClass: String,
    title: String,
    filter: String,
    extra: String = ""
): String {
    val safeTitle  = title.replace("'", "")
    val safeFilter = filter.replace("'", "")
    return """
        Add-Type -AssemblyName System.Windows.Forms
        ${'$'}d = New-Object System.Windows.Forms.$dialogClass
        ${'$'}d.Title  = '$safeTitle'
        ${'$'}d.Filter = '$safeFilter'
        ${if (extra.isNotBlank()) extra else ""}
        if (${'$'}d.ShowDialog() -eq 'OK') { Write-Output ${'$'}d.FileName }
    """.trimIndent()
}

private fun runPs(script: String): File? {
    val proc = ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script)
        .redirectErrorStream(true)
        .start()
    val path = proc.inputStream.bufferedReader().readText().trim()
    proc.waitFor()
    return path.takeIf { it.isNotBlank() }?.let { File(it) }
}
