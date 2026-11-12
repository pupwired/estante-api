package dev.pupwired

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.util.Base64
import java.util.UUID
import javax.sql.DataSource

/** Thrown anywhere in a route. StatusPages turns it into a JSON error. */
class ApiException(val status: HttpStatusCode, val code: String) : RuntimeException(code)

suspend fun <T> DataSource.query(block: (Connection) -> T): T =
    withContext(Dispatchers.IO) { connection.use(block) }

fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

fun newSecret(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private fun unauthorized() = ApiException(HttpStatusCode.Unauthorized, "unauthorized")

/**
 * Expects "Authorization: Bearer <userId>.<secret>", the value returned by POST /v1/users.
 * Only a SHA-256 hash of the secret is stored, so a database leak does not leak tokens.
 */
suspend fun ApplicationCall.requireUser(ds: DataSource): UUID {
    val header = request.headers[HttpHeaders.Authorization] ?: throw unauthorized()
    if (!header.startsWith("Bearer ")) throw unauthorized()
    val parts = header.removePrefix("Bearer ").split(".", limit = 2)
    if (parts.size != 2) throw unauthorized()
    val id = runCatching { UUID.fromString(parts[0]) }.getOrNull() ?: throw unauthorized()

    val storedHash = ds.query { c ->
        c.prepareStatement("SELECT token_hash FROM users WHERE id = ?").use { ps ->
            ps.setObject(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }
    val valid = storedHash != null &&
            MessageDigest.isEqual(storedHash.toByteArray(), sha256Hex(parts[1]).toByteArray())
    if (!valid) throw unauthorized()
    return id
}
