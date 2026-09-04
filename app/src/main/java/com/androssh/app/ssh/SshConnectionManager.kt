package com.androssh.app.ssh

import com.androssh.app.data.AuthMethod
import com.androssh.app.data.HostProfile
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session

class SshConnectionManager(
    private val knownHostsFile: File,
) {
    suspend fun openShell(
        profile: HostProfile,
        password: String?,
    ): ActiveSshSession = withContext(Dispatchers.IO) {
        require(profile.authMethod == AuthMethod.Password) { "Private key authentication is not implemented yet." }
        require(!password.isNullOrEmpty()) { "A saved password is required to connect." }

        knownHostsFile.parentFile?.mkdirs()
        if (!knownHostsFile.exists()) {
            knownHostsFile.createNewFile()
        }

        val client = SSHClient()
        client.loadKnownHosts(knownHostsFile)
        client.connect(profile.host, profile.port)
        client.authPassword(profile.username, password)
        val session = client.startSession()
        session.allocateDefaultPTY()
        val shell = session.startShell()
        ActiveSshSession(client, session, shell)
    }
}

class ActiveSshSession internal constructor(
    private val client: SSHClient,
    private val session: Session,
    private val shell: Session.Shell,
) : Closeable {
    fun readOutput(scope: CoroutineScope, onOutput: (String) -> Unit): Job = scope.launch(Dispatchers.IO) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
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
        const val DEFAULT_BUFFER_SIZE = 4096
    }
}
