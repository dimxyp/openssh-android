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

    @Query("SELECT * FROM host_profiles WHERE id = :id")
    suspend fun getProfile(id: Long): HostProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: HostProfileEntity): Long

    @Delete
    suspend fun delete(profile: HostProfileEntity)
}
