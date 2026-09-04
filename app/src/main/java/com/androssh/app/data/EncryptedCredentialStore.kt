package com.androssh.app.data

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class EncryptedCredentialStore(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        FILE_NAME,
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun savePassword(profileId: Long, password: String) {
        preferences.edit { putString(passwordKey(profileId), password) }
    }

    fun getPassword(profileId: Long): String? = preferences.getString(passwordKey(profileId), null)

    fun hasPassword(profileId: Long): Boolean = preferences.contains(passwordKey(profileId))

    fun deletePassword(profileId: Long) {
        preferences.edit { remove(passwordKey(profileId)) }
    }

    private fun passwordKey(profileId: Long): String = "profile.$profileId.password"

    private companion object {
        const val FILE_NAME = "androssh_credentials"
    }
}
