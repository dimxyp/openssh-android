package com.androssh.app.data.backup

import android.os.Build
import com.androssh.app.data.AuthMethod
import com.androssh.app.data.ConnectionRepository
import com.androssh.app.data.HostProfile
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

/** A connection profile as read from / written to an encrypted backup file. */
data class ExportedProfile(
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val authMethod: AuthMethod,
    val privateKeyAlias: String?,
    val password: String?,
)

enum class ImportConflictResolution { Skip, Overwrite, Duplicate }

/** An incoming profile whose host+username+port matches an already-saved connection. */
data class ImportConflict(
    val incoming: ExportedProfile,
    val existing: HostProfile,
)

data class ImportPlan(
    val newProfiles: List<ExportedProfile>,
    val conflicts: List<ImportConflict>,
)

/**
 * Handles encrypted export/import of saved connection profiles.
 *
 * Export file format: a small binary header (magic bytes, format version,
 * a one-byte key-derivation-function id, PBKDF2 salt, AES-GCM IV) followed
 * by an AES-256-GCM encrypted UTF-8 JSON payload derived from a
 * user-supplied passphrase. The KDF id lets newer devices (API 26+) use the
 * stronger PBKDF2WithHmacSHA256 while still allowing this app's minSdk 24
 * devices to fall back to PBKDF2WithHmacSHA1; the id is recorded per-file so
 * an export produced on one device can always be decrypted correctly on
 * another. The JSON document - which may contain saved passwords - only
 * ever exists in memory; the file written to disk is always ciphertext,
 * never plaintext credentials.
 */
class BackupManager(
    private val repository: ConnectionRepository,
) {
    suspend fun exportEncrypted(passphrase: CharArray, output: OutputStream) {
        val profiles = repository.getAllProfiles()
        val json = JSONObject()
        json.put("version", FORMAT_VERSION)
        json.put("exportedAt", System.currentTimeMillis())
        val array = JSONArray()
        profiles.forEach { profile ->
            val entry = JSONObject()
            entry.put("name", profile.name)
            entry.put("host", profile.host)
            entry.put("port", profile.port)
            entry.put("username", profile.username)
            entry.put("authMethod", profile.authMethod.name)
            entry.put("privateKeyAlias", profile.privateKeyAlias)
            entry.put("password", repository.getPassword(profile.id))
            array.put(entry)
        }
        json.put("profiles", array)

        val plaintext = json.toString().toByteArray(Charsets.UTF_8)
        val salt = ByteArray(SALT_SIZE_BYTES).also(SECURE_RANDOM::nextBytes)
        val iv = ByteArray(IV_SIZE_BYTES).also(SECURE_RANDOM::nextBytes)
        val kdfAlgorithm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) KDF_SHA256 else KDF_SHA1
        val key = deriveKey(kdfAlgorithm, passphrase, salt)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)

        output.write(MAGIC)
        output.write(byteArrayOf(FORMAT_VERSION.toByte()))
        output.write(byteArrayOf(kdfAlgorithm))
        output.write(salt)
        output.write(iv)
        output.write(ciphertext)
        output.flush()
    }

    /**
     * Decrypts [input] and compares its profiles against what is already
     * saved, splitting them into brand-new profiles and conflicts (same
     * host+username+port) that the caller must ask the user to resolve.
     */
    suspend fun buildImportPlan(passphrase: CharArray, input: InputStream): ImportPlan {
        val incomingProfiles = decrypt(passphrase, input)
        val newProfiles = mutableListOf<ExportedProfile>()
        val conflicts = mutableListOf<ImportConflict>()
        incomingProfiles.forEach { incoming ->
            val existing = repository.findByHostUsernamePort(incoming.host, incoming.username, incoming.port)
            if (existing != null) {
                conflicts.add(ImportConflict(incoming, existing))
            } else {
                newProfiles.add(incoming)
            }
        }
        return ImportPlan(newProfiles, conflicts)
    }

    suspend fun importNewProfile(profile: ExportedProfile) {
        repository.saveProfile(profile.toHostProfile(id = 0), profile.password)
    }

    suspend fun resolveConflict(conflict: ImportConflict, resolution: ImportConflictResolution) {
        when (resolution) {
            ImportConflictResolution.Skip -> Unit
            ImportConflictResolution.Overwrite -> repository.saveProfile(
                conflict.incoming.toHostProfile(id = conflict.existing.id),
                conflict.incoming.password,
            )
            ImportConflictResolution.Duplicate -> repository.saveProfile(
                conflict.incoming.toHostProfile(id = 0),
                conflict.incoming.password,
            )
        }
    }

    /**
     * Reads the whole (typically small, since it is a list of connection
     * profiles) encrypted file into memory before decrypting. If backup
     * files are ever expected to grow very large, this could be replaced by
     * reading just the fixed-size header first and then decrypting the
     * remainder as a stream via `CipherInputStream`.
     */
    private fun decrypt(passphrase: CharArray, input: InputStream): List<ExportedProfile> {
        val bytes = input.readBytes()
        val headerSize = MAGIC.size + 1 + 1 + SALT_SIZE_BYTES + IV_SIZE_BYTES
        require(bytes.size > headerSize) { "File is too small to be a valid AndroSSH backup." }

        var offset = 0
        val magic = bytes.copyOfRange(offset, offset + MAGIC.size)
        offset += MAGIC.size
        require(magic.contentEquals(MAGIC)) { "Not an AndroSSH backup file." }

        val version = bytes[offset].toInt()
        offset += 1
        require(version == FORMAT_VERSION) { "Unsupported backup format version $version." }

        val kdfAlgorithm = bytes[offset]
        offset += 1

        val salt = bytes.copyOfRange(offset, offset + SALT_SIZE_BYTES)
        offset += SALT_SIZE_BYTES
        val iv = bytes.copyOfRange(offset, offset + IV_SIZE_BYTES)
        offset += IV_SIZE_BYTES
        val ciphertext = bytes.copyOfRange(offset, bytes.size)

        val key = deriveKey(kdfAlgorithm, passphrase, salt)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plaintext = try {
            cipher.doFinal(ciphertext)
        } catch (error: Exception) {
            throw IllegalArgumentException("Wrong passphrase or corrupted backup file.", error)
        }

        val json = JSONObject(String(plaintext, Charsets.UTF_8))
        val array = json.getJSONArray("profiles")
        return (0 until array.length()).map { index ->
            val entry = array.getJSONObject(index)
            ExportedProfile(
                name = entry.getString("name"),
                host = entry.getString("host"),
                port = entry.getInt("port"),
                username = entry.getString("username"),
                authMethod = AuthMethod.valueOf(entry.getString("authMethod")),
                privateKeyAlias = entry.optString("privateKeyAlias", null),
                password = entry.optString("password", null),
            )
        }
    }

    /**
     * Derives the AES key from [passphrase]/[salt] using the PBKDF2 variant
     * identified by [kdfAlgorithm] ([KDF_SHA1] or [KDF_SHA256]). Exports
     * record which variant was used so a file can always be decrypted
     * regardless of which device (and Android version) it was later opened
     * on.
     */
    private fun deriveKey(kdfAlgorithm: Byte, passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val algorithmName = when (kdfAlgorithm) {
            KDF_SHA256 -> "PBKDF2WithHmacSHA256"
            KDF_SHA1 -> "PBKDF2WithHmacSHA1"
            else -> throw IllegalArgumentException("Unsupported key-derivation algorithm id $kdfAlgorithm.")
        }
        val factory = SecretKeyFactory.getInstance(algorithmName)
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_SIZE_BITS)
        val keyBytes = try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun ExportedProfile.toHostProfile(id: Long) = HostProfile(
        id = id,
        name = name,
        host = host,
        port = port,
        username = username,
        authMethod = authMethod,
        privateKeyAlias = privateKeyAlias,
        hasSavedPassword = !password.isNullOrEmpty(),
    )

    private companion object {
        val MAGIC = byteArrayOf('A'.code.toByte(), 'S'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
        const val FORMAT_VERSION = 1
        const val KDF_SHA1: Byte = 1
        const val KDF_SHA256: Byte = 2
        const val SALT_SIZE_BYTES = 16
        const val IV_SIZE_BYTES = 12
        const val KEY_SIZE_BITS = 256
        const val GCM_TAG_BITS = 128
        const val PBKDF2_ITERATIONS = 210_000
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        val SECURE_RANDOM = SecureRandom()
    }
}
