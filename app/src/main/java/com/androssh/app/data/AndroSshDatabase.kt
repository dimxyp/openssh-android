package com.androssh.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [HostProfileEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(AuthMethodConverter::class)
abstract class AndroSshDatabase : RoomDatabase() {
    abstract fun hostProfileDao(): HostProfileDao

    companion object {
        /**
         * Adds the `updatedAt` column used to track when a connection profile
         * was last saved or imported/exported, so backup import can decide
         * which side is newer when resolving duplicates.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE host_profiles ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): AndroSshDatabase = Room.databaseBuilder(
            context.applicationContext,
            AndroSshDatabase::class.java,
            "androssh.db",
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }
}

class AuthMethodConverter {
    @TypeConverter
    fun fromStorage(value: String): AuthMethod = AuthMethod.valueOf(value)

    @TypeConverter
    fun toStorage(value: AuthMethod): String = value.name
}
