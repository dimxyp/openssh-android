package com.androssh.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HostProfileDao {
    @Query("SELECT * FROM host_profiles ORDER BY name COLLATE NOCASE, host COLLATE NOCASE")
    fun observeProfiles(): Flow<List<HostProfileEntity>>

    @Query("SELECT * FROM host_profiles ORDER BY name COLLATE NOCASE, host COLLATE NOCASE")
    suspend fun getAllProfiles(): List<HostProfileEntity>

    @Query("SELECT * FROM host_profiles WHERE id = :id")
    suspend fun getProfile(id: Long): HostProfileEntity?

    @Query("SELECT * FROM host_profiles WHERE host = :host AND username = :username AND port = :port LIMIT 1")
    suspend fun findByHostUsernamePort(host: String, username: String, port: Int): HostProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: HostProfileEntity): Long

    @Delete
    suspend fun delete(profile: HostProfileEntity)
}
