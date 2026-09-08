package com.androssh.app.ssh

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NetworkReachabilityChecker {
    suspend fun isReachable(
        host: String,
        port: Int,
        timeoutMillis: Int = 2_000,
    ): Boolean = withContext(Dispatchers.IO) {
        if (host.isBlank() || port !in 1..65_535 || timeoutMillis <= 0) return@withContext false

        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMillis)
            }
            true
        } catch (_: IOException) {
            false
        }
    }
}
