package org.rigbyfoundation.nuggetvpn.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.rigbyfoundation.nuggetvpn.data.models.AppSettings
import org.rigbyfoundation.nuggetvpn.data.models.Profile
import java.io.File

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

class ProfileRepository(private val context: Context) {

    private val profilesFile: File
        get() = File(context.filesDir, "profiles.json")

    suspend fun loadProfiles(): List<Profile> {
        return try {
            if (profilesFile.exists()) {
                json.decodeFromString<List<Profile>>(profilesFile.readText())
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveProfiles(profiles: List<Profile>) {
        profilesFile.writeText(json.encodeToString(profiles))
    }

    suspend fun addProfile(profile: Profile) {
        val profiles = loadProfiles().toMutableList()
        profiles.add(profile)
        saveProfiles(profiles)
    }

    suspend fun deleteProfile(id: String) {
        val profiles = loadProfiles().filter { it.id != id }
        saveProfiles(profiles)
    }

    suspend fun deleteProfilesByIds(ids: List<String>) {
        val profiles = loadProfiles().filter { it.id !in ids }
        saveProfiles(profiles)
    }

    suspend fun deleteProfilesBySource(sourceDomain: String) {
        val profiles = loadProfiles().filter { it.sourceDomain != sourceDomain }
        saveProfiles(profiles)
    }

    suspend fun updateProfileUsage(id: String, up: Long, down: Long) {
        val profiles = loadProfiles().map {
            if (it.id == id) it.copy(
                totalUp = (it.totalUp ?: 0) + up,
                totalDown = (it.totalDown ?: 0) + down
            ) else it
        }
        saveProfiles(profiles)
    }
}

class SettingsRepository(private val context: Context) {

    private val settingsFile: File
        get() = File(context.filesDir, "settings.json")

    private object Keys {
        val THEME = stringPreferencesKey("theme")
    }

    suspend fun loadSettings(): AppSettings {
        return try {
            if (settingsFile.exists()) {
                json.decodeFromString<AppSettings>(settingsFile.readText())
            } else {
                AppSettings()
            }
        } catch (e: Exception) {
            AppSettings()
        }
    }

    suspend fun saveSettings(settings: AppSettings) {
        settingsFile.writeText(json.encodeToString(settings))
    }

    val themeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.THEME] ?: "system"
    }

    suspend fun getTheme(): String {
        return context.dataStore.data.first()[Keys.THEME] ?: "system"
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME] = theme
        }
    }
}
