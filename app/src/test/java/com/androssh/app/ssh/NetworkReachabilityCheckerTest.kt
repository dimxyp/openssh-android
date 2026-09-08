package com.androssh.app.ssh

import java.net.ServerSocket
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkReachabilityCheckerTest {
    private val checker = NetworkReachabilityChecker()

    @Test
    fun `reports reachable when a TCP port accepts connections`() = runBlocking {
        ServerSocket(0).use { server ->
            assertTrue(checker.isReachable("127.0.0.1", server.localPort))
        }
    }

    @Test
    fun `rejects invalid ports without connecting`() = runBlocking {
        assertFalse(checker.isReachable("127.0.0.1", 0))
    }
}
