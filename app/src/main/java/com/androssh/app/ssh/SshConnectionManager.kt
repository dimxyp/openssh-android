package com.androssh.app.ssh

import com.androssh.app.data.AuthMethod
import com.androssh.app.data.HostProfile
import java.io.Closeable
import java.io.File
import android.util.Base64
import java.security.PublicKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.connection.channel.direct.Session

class SshConnectionManager(
    private val knownHostsFile: File,
) {
    private val knownHostsLock = Any()
    suspend fun openShell(
        profile: HostProfile,
        password: String?,
    ): ActiveSshSession = withContext(Dispatchers.IO) {
        require(profile.authMethod == AuthMethod.Password) { "Private key authentication is not implemented yet." }
        require(!password.isNullOrEmpty()) { "A saved password is required to connect." }

        val client = SSHClient()
        client.addHostKeyVerifier(AppKnownHostsVerifier(knownHostsFile, knownHostsLock))
        client.connect(profile.host, profile.port)
        client.authPassword(profile.username, password)
        val session = client.startSession()
        session.allocateDefaultPTY()
        val shell = session.startShell()
        ActiveSshSession(client, session, shell)
    }
}

private class AppKnownHostsVerifier(
    private val knownHostsFile: File,
    private val lock: Any,
) : HostKeyVerifier {
    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean = synchronized(lock) {
        ensureKnownHostsFile()

        val hostId = "$hostname:$port"
        val encodedKey = Base64.encodeToString(key.encoded, Base64.NO_WRAP)
        val expectedLine = "$hostId ${KeyType.fromKey(key)} $encodedKey"
        val knownHosts = knownHostsFile.readLines()
        val existing = knownHosts.firstOrNull { it.startsWith("$hostId ") }

        when {
            existing == expectedLine -> true
            existing == null -> {
                knownHostsFile.appendText("$expectedLine\n")
                true
            }
            else -> false
        }
    }

    override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = synchronized(lock) {
        ensureKnownHostsFile()
        val hostId = "$hostname:$port"
        knownHostsFile.readLines()
            .filter { it.startsWith("$hostId ") }
            .mapNotNull { it.split(' ').getOrNull(1) }
    }

    private fun ensureKnownHostsFile() {
        knownHostsFile.parentFile?.mkdirs()
        if (!knownHostsFile.exists()) {
            knownHostsFile.createNewFile()
        }
    }
}

class ActiveSshSession internal constructor(
    private val client: SSHClient,
    private val session: Session,
    private val shell: Session.Shell,
) : Closeable {
    fun readOutput(scope: CoroutineScope, onOutput: (String) -> Unit): Job = scope.launch(Dispatchers.IO) {
        val buffer = ByteArray(SHELL_READ_BUFFER_SIZE)
        while (isActive && !shell.isEOF) {
            val read = shell.inputStream.read(buffer)
            if (read < 0) break
            if (read > 0) {
                onOutput(String(buffer, 0, read))
            }
        }
    }

    suspend fun sendInput(input: String) = withContext(Dispatchers.IO) {
        shell.outputStream.write(input.toByteArray())
        shell.outputStream.flush()
    }

    override fun close() {
        runCatching { shell.close() }
        runCatching { session.close() }
        runCatching { client.disconnect() }
        runCatching { client.close() }
    }

    private companion object {
        const val SHELL_READ_BUFFER_SIZE = 4096
    }
}
