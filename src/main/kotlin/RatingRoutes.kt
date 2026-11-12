package dev.pupwired

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

@Serializable
data class RatingRequest(val score: Int, val clientUpdatedAt: Long)

private val WORK_ID = Regex("^OL\\d{1,12}W$")

// An older retried request never overwrites a newer rating
private const val UPSERT = """
    INSERT INTO ratings (user_id, work_id, score, client_updated_at)
    VALUES (?, ?, ?, ?)
    ON CONFLICT (user_id, work_id) DO UPDATE
      SET score = EXCLUDED.score,
          client_updated_at = EXCLUDED.client_updated_at,
          updated_at = now()
      WHERE ratings.client_updated_at < EXCLUDED.client_updated_at
"""

fun Route.ratingRoutes(ds: DataSource) {
    route("/books/{workId}/rating") {

        put {
            val user = call.requireUser(ds)
            val workId = call.parameters["workId"]?.takeIf { WORK_ID.matches(it) }
                ?: throw ApiException(HttpStatusCode.BadRequest, "invalid_work_id")
            val body = runCatching { call.receive<RatingRequest>() }
                .getOrElse { throw ApiException(HttpStatusCode.BadRequest, "invalid_body") }
            if (body.score !in 1..5) throw ApiException(HttpStatusCode.BadRequest, "invalid_score")

            // A client clock in the future would block every later update, so clamp to "now".
            val stamp = Instant.ofEpochMilli(
                body.clientUpdatedAt.coerceIn(0, System.currentTimeMillis())
            )

            try {
                ds.query { c ->
                    c.prepareStatement(UPSERT).use { ps ->
                        ps.setObject(1, user)
                        ps.setString(2, workId)
                        ps.setInt(3, body.score)
                        ps.setTimestamp(4, Timestamp.from(stamp))
                        ps.executeUpdate()
                    }
                }
            } catch (e: SQLException) {
                // 23503 = foreign key violation: the book is not in the books table yet
                if (e.sqlState == "23503") throw ApiException(HttpStatusCode.NotFound, "unknown_book")
                throw e
            }
            call.respond(HttpStatusCode.NoContent)
        }

        delete {
            val user = call.requireUser(ds)
            val workId = call.parameters["workId"]?.takeIf { WORK_ID.matches(it) }
                ?: throw ApiException(HttpStatusCode.BadRequest, "invalid_work_id")
            ds.query { c ->
                c.prepareStatement("DELETE FROM ratings WHERE user_id = ? AND work_id = ?").use { ps ->
                    ps.setObject(1, user)
                    ps.setString(2, workId)
                    ps.executeUpdate()
                }
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
