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
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive
import net.schmizz.sshj.userauth.method.AuthMethod as SshAuthMethod
import net.schmizz.sshj.userauth.method.AuthPassword
import net.schmizz.sshj.userauth.method.ChallengeResponseProvider
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
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

    /**
     * Opens an interactive shell with a PTY of exactly [cols] x [rows] characters, so the remote
     * side wraps lines and lays out full-screen programs (`vim`, `top`, ...) for the size actually
     * visible on the device instead of a default guess.
     */
    suspend fun openShell(
        profile: HostProfile,
        password: String?,
        cols: Int,
        rows: Int,
    ): ActiveSshSession = withContext(Dispatchers.IO) {
        val client = connectAndAuthenticate(profile, password)
        val session = client.startSession()
        session.allocatePTY(
            TERM_TYPE,
            cols.coerceAtLeast(1),
            rows.coerceAtLeast(1),
            0,
            0,
            emptyMap(),
        )
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
        val passwordFinder = object : PasswordFinder {
            override fun reqPassword(resource: Resource<*>): CharArray = password.toCharArray()

            override fun shouldRetry(resource: Resource<*>): Boolean = false
        }
        val challengeResponseProvider = object : ChallengeResponseProvider {
            override fun getSubmethods(): List<String> = emptyList()

            override fun init(resource: Resource<*>, name: String, instruction: String) = Unit

            override fun getResponse(prompt: String, echo: Boolean): CharArray = password.toCharArray()

            override fun shouldRetry(): Boolean = false
        }
        // Some servers expose password-backed PAM only as keyboard-interactive, so offer both.
        val methods: List<SshAuthMethod> = listOf(
            AuthPassword(passwordFinder),
            AuthKeyboardInteractive(challengeResponseProvider),
        )
        client.auth(profile.username, methods)
        return client
    }

    private companion object {
        /** Terminal type reported to the server; matches the ANSI/SGR subset the emulator renders. */
        const val TERM_TYPE = "xterm-256color"

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

    /**
     * Tells the server that the terminal window is now [cols] x [rows] characters (SSH
     * `window-change`), so running programs re-layout - the equivalent of `SIGWINCH` locally.
     */
    suspend fun resize(cols: Int, rows: Int) = withContext(Dispatchers.IO) {
        shell.changeWindowDimensions(cols.coerceAtLeast(1), rows.coerceAtLeast(1), 0, 0)
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
