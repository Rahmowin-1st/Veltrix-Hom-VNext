package com.veltrix.hom.vnext.server

import io.ktor.http.RequestConnectionPoint
import io.ktor.server.plugins.origin as ktorOrigin
import io.ktor.server.request.ApplicationRequest

/**
 * Keeps public auth rate-limit addressing on Ktor's supported request-origin API.
 * Ktor's origin falls back to local connection data unless forwarded-header plugins are installed.
 */
val ApplicationRequest.origin: RequestConnectionPoint
    get() = this.ktorOrigin
