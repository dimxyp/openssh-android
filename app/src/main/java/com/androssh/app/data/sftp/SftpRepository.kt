package com.androssh.app.data.sftp

import com.androssh.app.data.HostProfile
import com.androssh.app.ssh.SftpSession
import com.androssh.app.ssh.SshConnectionManager
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.RemoteResourceInfo

/** A single entry (file or directory) inside a remote SFTP directory listing. */
data class SftpEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedEpochSeconds: Long,
)

/** Reports progress for an in-flight upload/download/server-to-server transfer. */
data class TransferProgress(
    val bytesTransferred: Long,
    val totalBytes: Long,
) {
    val percent: Int
        get() = if (totalBytes <= 0) 0 else ((bytesTransferred * 100) / totalBytes).toInt()
}

/** Thrown to unwind a transfer loop when the caller requested cancellation. */
class TransferCancelledException : Exception("Transfer cancelled")

private const val COPY_BUFFER_SIZE = 32 * 1024

/**
 * Repository layer wrapping sshj's [net.schmizz.sshj.sftp.SFTPClient] to
 * provide directory browsing and file-transfer operations for the SFTP
 * screens. All blocking I/O runs on [Dispatchers.IO].
 */
class SftpRepository(
    private val sshConnectionManager: SshConnectionManager,
) {
    suspend fun connect(profile: HostProfile, password: String?): SftpSession =
        sshConnectionManager.openSftp(profile, password)

    suspend fun list(session: SftpSession, path: String): List<SftpEntry> = withContext(Dispatchers.IO) {
        session.sftpClient.ls(path)
            .map { it.toEntry() }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    suspend fun mkdir(session: SftpSession, path: String) = withContext(Dispatchers.IO) {
        session.sftpClient.mkdir(path)
    }

    suspend fun delete(session: SftpSession, entry: SftpEntry) = withContext(Dispatchers.IO) {
        if (entry.isDirectory) session.sftpClient.rmdir(entry.path) else session.sftpClient.rm(entry.path)
    }

    suspend fun rename(session: SftpSession, fromPath: String, toPath: String) = withContext(Dispatchers.IO) {
        session.sftpClient.rename(fromPath, toPath)
    }

    /** Uploads a local stream (e.g. obtained via SAF `ContentResolver`) to the remote path. */
    suspend fun upload(
        session: SftpSession,
        input: InputStream,
        remotePath: String,
        totalBytes: Long,
        isCancelled: () -> Boolean,
        onProgress: (TransferProgress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        session.sftpClient.open(remotePath, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)).use { remoteFile ->
            remoteFile.RemoteFileOutputStream().use { output ->
                copyWithProgress(input, output, totalBytes, isCancelled, onProgress)
            }
        }
    }

    /** Downloads a remote file into a local stream (e.g. from a SAF `ContentResolver` output stream). */
    suspend fun download(
        session: SftpSession,
        entry: SftpEntry,
        output: OutputStream,
        isCancelled: () -> Boolean,
        onProgress: (TransferProgress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        session.sftpClient.open(entry.path, EnumSet.of(OpenMode.READ)).use { remoteFile ->
            remoteFile.RemoteFileInputStream().use { input ->
                copyWithProgress(input, output, entry.size, isCancelled, onProgress)
            }
        }
    }

    /**
     * Copies a file directly between two saved SSH servers. The SFTP protocol
     * has no server-to-server ("third-party") copy operation, so this reads
     * the source file and writes the destination file concurrently through
     * this device's memory - i.e. a "transfer via device" - without ever
     * persisting the full file to local disk. Progress reflects bytes copied
     * so far relative to the source file size.
     */
    suspend fun transferBetweenServers(
        source: SftpSession,
        sourceEntry: SftpEntry,
        destination: SftpSession,
        destinationPath: String,
        isCancelled: () -> Boolean,
        onProgress: (TransferProgress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        source.sftpClient.open(sourceEntry.path, EnumSet.of(OpenMode.READ)).use { sourceFile ->
            destination.sftpClient.open(
                destinationPath,
                EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC),
            ).use { destinationFile ->
                sourceFile.RemoteFileInputStream().use { input ->
                    destinationFile.RemoteFileOutputStream().use { output ->
                        copyWithProgress(input, output, sourceEntry.size, isCancelled, onProgress)
                    }
                }
            }
        }
    }

    private fun copyWithProgress(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long,
        isCancelled: () -> Boolean,
        onProgress: (TransferProgress) -> Unit,
    ) {
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        var transferred = 0L
        onProgress(TransferProgress(0, totalBytes))
        while (true) {
            if (isCancelled()) throw TransferCancelledException()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            transferred += read
            onProgress(TransferProgress(transferred, totalBytes))
        }
        output.flush()
    }
}

private fun RemoteResourceInfo.toEntry(): SftpEntry = SftpEntry(
    name = name,
    path = path,
    isDirectory = isDirectory,
    size = attributes.size,
    modifiedEpochSeconds = attributes.mtime,
)
