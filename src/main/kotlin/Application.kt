package dev.pupwired

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
data class Health(val status: String, val db: Boolean)

@Serializable
data class ApiError(val error: String)

fun Application.module() {
    val ds = createDataSource(environment.config)
    monitor.subscribe(ApplicationStopped) { ds.close() }
    install(ContentNegotiation) { json() }
    install(CallLogging)
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled error", cause)
            call.respond(HttpStatusCode.InternalServerError, ApiError("internal_error"))
        }
    }

    routing {
        get("/health") {
            val dbOk = runCatching {
                ds.connection.use { c -> c.createStatement().use { it.execute("SELECT 1") } }
            }.isSuccess
            call.respond(Health("ok", dbOk))
        }
    }
}

private fun createDataSource(config: ApplicationConfig): HikariDataSource =
    HikariDataSource(HikariConfig().apply {
        jdbcUrl = config.property("db.url").getString()
        username = config.property("db.user").getString()
        password = config.property("db.password").getString()
        maximumPoolSize = 3
        minimumIdle = 0
        connectionTimeout = 5000
        initializationFailTimeout = -1
    })