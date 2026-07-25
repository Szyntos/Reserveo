package org.julsz.smnt

import io.ktor.util.*

/**
 * Holds the current staff member's HTTP Basic credentials for the lifetime of the session.
 * Read by the shared HttpClient's default-request header on every call; cleared on logout.
 * Only two staff accounts ever use this app, so a re-sent Basic header (no token/refresh) is
 * simple enough and avoids building out session/token expiry machinery.
 */
object AuthSession {
    var basicAuthHeader: String? = null
        private set

    fun set(email: String, password: String) {
        basicAuthHeader = "Basic " + "$email:$password".encodeBase64()
    }

    fun clear() {
        basicAuthHeader = null
    }
}
