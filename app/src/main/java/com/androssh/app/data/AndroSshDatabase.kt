package com.androssh.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Database(
    entities = [HostProfileEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(AuthMethodConverter::class)
abstract class AndroSshDatabase : RoomDatabase() {
    abstract fun hostProfileDao(): HostProfileDao

    companion object {
        fun create(context: Context): AndroSshDatabase = Room.databaseBuilder(
            context.applicationContext,
            AndroSshDatabase::class.java,
            "androssh.db",
        ).build()
    }
}

class AuthMethodConverter {
    @TypeConverter
    fun fromStorage(value: String): AuthMethod = AuthMethod.valueOf(value)

    @TypeConverter
    fun toStorage(value: AuthMethod): String = value.name
}
