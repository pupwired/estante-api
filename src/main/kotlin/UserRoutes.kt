package dev.pupwired

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import java.util.UUID
import javax.sql.DataSource

@Serializable
data class NewUser(val userId: String, val token: String)

fun Route.userRoutes(ds: DataSource) {
    // Anonymous registration. The app calls this once and stores the token on the device.
    post("/users") {
        val secret = newSecret()
        val id = ds.query { c ->
            c.prepareStatement("INSERT INTO users (token_hash) VALUES (?) RETURNING id").use { ps ->
                ps.setString(1, sha256Hex(secret))
                ps.executeQuery().use { rs ->
                    rs.next()
                    rs.getObject(1, UUID::class.java)
                }
            }
        }
        call.respond(HttpStatusCode.Created, NewUser(id.toString(), "$id.$secret"))
    }

    // Account deletion. ON DELETE CASCADE removes this user's ratings.
    delete("/me") {
        val user = call.requireUser(ds)
        ds.query { c ->
            c.prepareStatement("DELETE FROM users WHERE id = ?").use { ps ->
                ps.setObject(1, user)
                ps.executeUpdate()
            }
        }
        call.respond(HttpStatusCode.NoContent)
    }
}
