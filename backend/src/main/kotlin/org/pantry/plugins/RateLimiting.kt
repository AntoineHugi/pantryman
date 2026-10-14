package org.pantry.plugins

import io.ktor.http.HttpHeaders
import io.ktor.server.application.*
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.minutes

val AUTH_RATE_LIMIT = RateLimitName("auth")

fun Application.configureRateLimiting() {
    install(RateLimit) {
        register(AUTH_RATE_LIMIT) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
            // Behind Koyeb's proxy the socket address is the proxy's, so key on the
            // client IP the proxy appends to X-Forwarded-For (the last entry).
            requestKey { call ->
                call.request.headers[HttpHeaders.XForwardedFor]
                    ?.split(",")
                    ?.lastOrNull()
                    ?.trim()
                    ?: call.request.origin.remoteHost
            }
        }
    }
}
