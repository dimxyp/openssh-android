package com.androssh.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConnectionRepository(
    private val dao: HostProfileDao,
    private val credentialStore: EncryptedCredentialStore,
) {
    fun observeProfiles(): Flow<List<HostProfile>> = dao.observeProfiles().map { profiles ->
        profiles.map { profile -> profile.toModel(credentialStore.hasPassword(profile.id)) }
    }

    suspend fun getAllProfiles(): List<HostProfile> = dao.getAllProfiles().map { profile ->
        profile.toModel(credentialStore.hasPassword(profile.id))
    }

    suspend fun getProfile(id: Long): HostProfile? = dao.getProfile(id)?.let { profile ->
        profile.toModel(credentialStore.hasPassword(profile.id))
    }

    suspend fun findByHostUsernamePort(host: String, username: String, port: Int): HostProfile? =
        dao.findByHostUsernamePort(host, username, port)?.let { profile ->
            profile.toModel(credentialStore.hasPassword(profile.id))
        }

    suspend fun saveProfile(profile: HostProfile, password: String?): Long {
        val savedId = dao.upsert(profile.toEntity().copy(updatedAt = System.currentTimeMillis()))
        if (profile.authMethod == AuthMethod.Password && !password.isNullOrBlank()) {
            credentialStore.savePassword(savedId, password)
        }
        if (profile.authMethod != AuthMethod.Password) {
            credentialStore.deletePassword(savedId)
        }
        return savedId
    }

    suspend fun deleteProfile(profile: HostProfile) {
        dao.delete(profile.toEntity())
        credentialStore.deletePassword(profile.id)
    }

    fun getPassword(profileId: Long): String? = credentialStore.getPassword(profileId)
}
