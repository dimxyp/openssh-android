package com.androssh.app

import android.app.Application
import com.androssh.app.data.AndroSshDatabase
import com.androssh.app.data.ConnectionRepository
import com.androssh.app.data.EncryptedCredentialStore
import com.androssh.app.data.backup.BackupManager
import com.androssh.app.data.sftp.SftpRepository
import com.androssh.app.ssh.ForegroundServiceKeepAlive
import com.androssh.app.ssh.SshConnectionManager
import com.androssh.app.ssh.SshSessionHolder
import java.io.File

class AndroSshApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val database = AndroSshDatabase.create(this)
        val credentialStore = EncryptedCredentialStore(this)
        val connectionRepository = ConnectionRepository(
            dao = database.hostProfileDao(),
            credentialStore = credentialStore,
        )
        val sshConnectionManager = SshConnectionManager(
            knownHostsFile = File(filesDir, "ssh/known_hosts"),
        )
        container = AppContainer(
            connectionRepository = connectionRepository,
            sshConnectionManager = sshConnectionManager,
            // Application-scoped so the live shell (and its scrollback) survives Activity and
            // ViewModel destruction; SshForegroundService keeps this process alive meanwhile.
            sshSessionHolder = SshSessionHolder(
                connectionManager = sshConnectionManager,
                keepAlive = ForegroundServiceKeepAlive(this),
            ),
            sftpRepository = SftpRepository(sshConnectionManager),
            backupManager = BackupManager(connectionRepository),
        )
    }
}

data class AppContainer(
    val connectionRepository: ConnectionRepository,
    val sshConnectionManager: SshConnectionManager,
    val sshSessionHolder: SshSessionHolder,
    val sftpRepository: SftpRepository,
    val backupManager: BackupManager,
)
