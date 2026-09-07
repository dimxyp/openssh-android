package com.androssh.app.ssh

import com.androssh.app.data.AuthMethod
import com.androssh.app.data.HostProfile
import java.io.Closeable
import java.io.File
import android.util.Base64
import java.security.PublicKey
import java.security.Security
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.connection.channel.direct.Session
import org.bouncycastle.jce.provider.BouncyCastleProvider

class SshConnectionManager(
    private val knownHostsFile: File,
) {
    private val knownHostsLock = Any()

    init {
        ensureBouncyCastleProviderRegistered()
    }

    suspend fun openShell(
        profile: HostProfile,
        password: String?,
    ): ActiveSshSession = withContext(Dispatchers.IO) {
        val client = connectAndAuthenticate(profile, password)
        val session = client.startSession()
        session.allocateDefaultPTY()
        val shell = session.startShell()
        ActiveSshSession(client, session, shell)
    }

    /** Opens a dedicated connection for SFTP file-transfer operations. */
    suspend fun openSftp(
        profile: HostProfile,
        password: String?,
    ): SftpSession = withContext(Dispatchers.IO) {
        val client = connectAndAuthenticate(profile, password)
        SftpSession(client, client.newSFTPClient())
    }

    private fun connectAndAuthenticate(profile: HostProfile, password: String?): SSHClient {
        require(profile.authMethod == AuthMethod.Password) { "Private key authentication is not implemented yet." }
        require(!password.isNullOrEmpty()) { "A saved password is required to connect." }

        val client = SSHClient()
        client.addHostKeyVerifier(AppKnownHostsVerifier(knownHostsFile, knownHostsLock))
        client.connect(profile.host, profile.port)
        client.authPassword(profile.username, password)
        return client
    }

    private companion object {
        @Volatile
        private var bouncyCastleRegistered = false

        /**
         * Registers the real `org.bouncycastle` JCE provider so sshj can negotiate modern
         * algorithms (notably curve25519-sha256 / X25519 key exchange).
         *
         * Android ships its own built-in security provider that is *also* named "BC" (backed by
         * Conscrypt/AndroidOpenSSL), but it is incomplete and shadows the real BouncyCastle classes
         * pulled in transitively via sshj -> bcprov-jdk18on. Left alone, `Security.getProvider("BC")`
         * resolves to Android's limited provider and connecting fails with
         * "no such algorithm: X25519 for provider BC". Explicitly removing Android's "BC" entry and
         * inserting the real [BouncyCastleProvider] at the highest priority fixes this. This only
         * affects JCE algorithm lookups by provider name/priority for our (and sshj's) own crypto
         * use; it does not change how Android Keystore or EncryptedSharedPreferences resolve their
         * own dedicated providers (e.g. "AndroidKeyStore"), so those paths are unaffected.
         *
         * Do NOT remove this registration - without it modern OpenSSH servers that offer
         * curve25519-sha256 key exchange cannot be connected to.
         */
        @Synchronized
        private fun ensureBouncyCastleProviderRegistered() {
            if (bouncyCastleRegistered) return
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.insertProviderAt(BouncyCastleProvider(), 1)
            SecurityUtils.setRegisterBouncyCastle(true)
            bouncyCastleRegistered = true
        }
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

/**
 * A live SSH connection dedicated to SFTP operations. Owns both the
 * underlying [SSHClient] and the [SFTPClient] built on top of it so both can
 * be torn down together via [close].
 */
class SftpSession internal constructor(
    private val client: SSHClient,
    val sftpClient: SFTPClient,
) : Closeable {
    override fun close() {
        runCatching { sftpClient.close() }
        runCatching { client.disconnect() }
        runCatching { client.close() }
    }
}
