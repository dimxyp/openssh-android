package com.androssh.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "host_profiles")
data class HostProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authMethod: AuthMethod = AuthMethod.Password,
    val privateKeyAlias: String? = null,
    val updatedAt: Long = 0L,
)

data class HostProfile(
    val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authMethod: AuthMethod = AuthMethod.Password,
    val privateKeyAlias: String? = null,
    val hasSavedPassword: Boolean = false,
    val updatedAt: Long = 0L,
)

fun HostProfileEntity.toModel(hasSavedPassword: Boolean) = HostProfile(
    id = id,
    name = name,
    host = host,
    port = port,
    username = username,
    authMethod = authMethod,
    privateKeyAlias = privateKeyAlias,
    hasSavedPassword = hasSavedPassword,
    updatedAt = updatedAt,
)

fun HostProfile.toEntity() = HostProfileEntity(
    id = id,
    name = name,
    host = host,
    port = port,
    username = username,
    authMethod = authMethod,
    privateKeyAlias = privateKeyAlias,
    updatedAt = updatedAt,
)
