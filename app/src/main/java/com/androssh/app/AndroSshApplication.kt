package com.androssh.app

import android.app.Application
import com.androssh.app.data.AndroSshDatabase
import com.androssh.app.data.ConnectionRepository
import com.androssh.app.data.EncryptedCredentialStore
import com.androssh.app.data.backup.BackupManager
import com.androssh.app.data.sftp.SftpRepository
import com.androssh.app.ssh.SshConnectionManager
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
            sftpRepository = SftpRepository(sshConnectionManager),
            backupManager = BackupManager(connectionRepository),
        )
    }
}

data class AppContainer(
    val connectionRepository: ConnectionRepository,
    val sshConnectionManager: SshConnectionManager,
    val sftpRepository: SftpRepository,
    val backupManager: BackupManager,
)
