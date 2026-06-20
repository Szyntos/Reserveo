package org.julsz.smnt

const val DEPLOYMENT_URL = "https://reserveo-production.up.railway.app"

val LOCALHOST_URL: String get() = if (IS_ANDROID) "http://10.0.2.2:8080" else "http://localhost:8080"

fun resolveServerUrl(mode: String, customUrl: String): String = when (mode) {
    "deployment" -> DEPLOYMENT_URL
    "custom"     -> customUrl.ifBlank { LOCALHOST_URL }
    else         -> LOCALHOST_URL
}
