package org.julsz.smnt.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.julsz.smnt.db.UserHotelRoles
import org.julsz.smnt.db.Users

/**
 * Validates credentials against the users table. If the matched user has no password set yet,
 * the supplied password is adopted as their password (first-login bootstrap) — there is no
 * separate signup/admin flow since this app only ever has a couple of staff accounts.
 */
fun authenticateUser(email: String, password: String): AuthPrincipal? = transaction {
    val row = Users.selectAll().where { Users.email eq email }.firstOrNull() ?: return@transaction null
    val storedHash = row[Users.passwordHash]

    val ok = if (storedHash.isNullOrBlank()) {
        Users.update({ Users.id eq row[Users.id] }) { it[passwordHash] = PasswordHasher.hash(password) }
        true
    } else {
        PasswordHasher.verify(password, storedHash)
    }

    if (!ok) return@transaction null
    AuthPrincipal(
        userId  = row[Users.id],
        name    = row[Users.name],
        email   = row[Users.email],
        appRole = row[Users.appRole]
    )
}

/** Returns false (and responds 403) unless the current principal is an admin. */
suspend fun ApplicationCall.requireAdmin(): Boolean {
    val principal = principal<AuthPrincipal>()
    if (principal?.appRole != "admin") {
        respond(HttpStatusCode.Forbidden, "Admin role required")
        return false
    }
    return true
}

private fun hotelRoleFor(userId: Int, hotelId: Int): String? = transaction {
    UserHotelRoles.selectAll()
        .where { (UserHotelRoles.userId eq userId) and (UserHotelRoles.hotelId eq hotelId) }
        .firstOrNull()?.get(UserHotelRoles.role)
}

/**
 * Returns false (and responds 401/403) unless the current principal holds one of [allowed] roles
 * for [hotelId]. A global `app_role = admin` user is always allowed, regardless of hotel role.
 */
suspend fun ApplicationCall.requireHotelRole(hotelId: Int, allowed: Set<String>): Boolean {
    val principal = principal<AuthPrincipal>()
        ?: run { respond(HttpStatusCode.Unauthorized); return false }
    if (principal.appRole == "admin") return true
    val role = hotelRoleFor(principal.userId, hotelId)
    if (role == null || role !in allowed) {
        respond(HttpStatusCode.Forbidden, "Insufficient role for this hotel")
        return false
    }
    return true
}

/** Hotel-scoped admin: full control (config + reservations) within that hotel. */
suspend fun ApplicationCall.requireHotelAdmin(hotelId: Int): Boolean =
    requireHotelRole(hotelId, setOf("admin"))

/** Hotel-scoped manager or admin: reservation-workflow endpoints, excludes config endpoints. */
suspend fun ApplicationCall.requireHotelManager(hotelId: Int): Boolean =
    requireHotelRole(hotelId, setOf("admin", "manager"))

/**
 * For endpoints not scoped to a single hotel (e.g. guests, which are shared across hotels):
 * requires manager or admin role in at least one hotel. Viewers are blocked.
 */
suspend fun ApplicationCall.requireAnyManagerRole(): Boolean {
    val principal = principal<AuthPrincipal>()
        ?: run { respond(HttpStatusCode.Unauthorized); return false }
    if (principal.appRole == "admin") return true
    val hasRole = transaction {
        UserHotelRoles.selectAll()
            .where { (UserHotelRoles.userId eq principal.userId) and (UserHotelRoles.role inList listOf("admin", "manager")) }
            .count() > 0
    }
    if (!hasRole) {
        respond(HttpStatusCode.Forbidden, "Manager or admin role required")
        return false
    }
    return true
}
