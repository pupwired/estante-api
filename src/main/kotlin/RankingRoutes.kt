package dev.pupwired

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.sql.ResultSet
import java.util.Base64
import javax.sql.DataSource

private const val MIN_VOTES = 3
private const val DEFAULT_LIMIT = 20
private const val MAX_LIMIT = 50

@Serializable
data class RankingItem(
    val workId: String,
    val title: String,
    val author: String?,
    val coverId: Int?,
    val ratingAvg: Double?,
    val ratingCount: Int,
    val criticAvg: Double?,
    val criticCount: Int,
    val score: Double,
)

@Serializable
data class RankingPage(val items: List<RankingItem>, val nextCursor: String?)

private class Row(val item: RankingItem, val score: BigDecimal)

private fun encodeCursor(row: Row): String =
    Base64.getUrlEncoder().withoutPadding()
        .encodeToString("${row.score.toPlainString()}|${row.item.workId}".toByteArray())

private fun decodeCursor(value: String): Pair<BigDecimal, String> =
    try {
        val raw = String(Base64.getUrlDecoder().decode(value))
        val parts = raw.split("|", limit = 2)
        BigDecimal(parts[0]) to parts[1]
    } catch (e: Exception) {
        throw ApiException(HttpStatusCode.BadRequest, "invalid_cursor")
    }

private fun ResultSet.toRow(): Row {
    val score = getBigDecimal("bayes_score")
    val coverId = getInt("cover_id").takeIf { !wasNull() }
    return Row(
        RankingItem(
            workId = getString("work_id"),
            title = getString("title"),
            author = getString("author"),
            coverId = coverId,
            ratingAvg = getBigDecimal("rating_avg")?.toDouble(),
            ratingCount = getInt("rating_count"),
            criticAvg = getBigDecimal("critic_avg")?.toDouble(),
            criticCount = getInt("critic_count"),
            score = score.toDouble(),
        ),
        score,
    )
}

fun Route.rankingRoutes(ds: DataSource) {
    get("/ranking") {
        val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_LIMIT)
            .coerceIn(1, MAX_LIMIT)
        val cursor = call.request.queryParameters["cursor"]?.let(::decodeCursor)

        val rows = ds.query { c ->
            val sql = buildString {
                append(
                    """
                    SELECT b.work_id, b.title, b.author, b.cover_id,
                           s.rating_avg, s.rating_count, s.critic_avg, s.critic_count, s.bayes_score
                    FROM book_stats s
                    JOIN books b ON b.work_id = s.work_id
                    WHERE s.rating_count >= ? AND s.bayes_score IS NOT NULL
                    """.trimIndent()
                )
                if (cursor != null) append("\n AND (s.bayes_score, s.work_id) < (?, ?)")
                append("\n ORDER BY s.bayes_score DESC, s.work_id DESC LIMIT ?")
            }
            c.prepareStatement(sql).use { ps ->
                var i = 1
                ps.setInt(i++, MIN_VOTES)
                if (cursor != null) {
                    ps.setBigDecimal(i++, cursor.first)
                    ps.setString(i++, cursor.second)
                }
                ps.setInt(i, limit + 1)           // one extra row tells us if there is a next page
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toRow()) } }
            }
        }

        val page = rows.take(limit)
        val next = if (rows.size > limit) encodeCursor(page.last()) else null

        // max-age: the app/browser may reuse it for 1 min.
        // s-maxage: the Cloudflare edge may reuse it for 10 min.
        call.response.header(HttpHeaders.CacheControl, "public, max-age=60, s-maxage=600")
        call.respond(RankingPage(page.map { it.item }, next))
    }
}
