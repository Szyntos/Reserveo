package org.julsz.smnt

import java.io.File

suspend fun saveFilePicker(title: String, defaultName: String, filter: String): File? = null

actual suspend fun openFilePicker(title: String, filter: String): String? = null
