package org.pantry.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.minutes

val AUTH_RATE_LIMIT = RateLimitName("auth")
val JOIN_RATE_LIMIT = RateLimitName("household-join")

fun Application.configureRateLimiting() {
    install(RateLimit) {
        register(AUTH_RATE_LIMIT) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
            requestKey { call -> call.clientIp() }
        }
        register(JOIN_RATE_LIMIT) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
            requestKey { call -> call.clientIp() }
        }
    }
}

// Render sits behind Cloudflare, which sets CF-Connecting-IP to the real client IP and
// overwrites any value the client sent. X-Forwarded-For can't be used: clients can
// prepend to it, and its last entry is a proxy address that changes per request.
// Without the header (local dev) the socket address is the client.
// If the backend ever moves off Cloudflare, this header becomes spoofable: revisit.
fun ApplicationCall.clientIp(): String =
    request.header("CF-Connecting-IP")?.trim()?.takeIf { it.isNotEmpty() }
        ?: request.origin.remoteHost
