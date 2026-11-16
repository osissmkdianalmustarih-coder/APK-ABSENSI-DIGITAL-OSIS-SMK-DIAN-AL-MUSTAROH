package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.local.UniformLabels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "absensi_osis_prefs")

data class AppSettings(
    val isDarkTheme: Boolean = false,
    val soundEnabled: Boolean = true,
    val schoolName: String = "SMK DIAN AL MUSTARIH",
    val attendanceEnabled: Boolean = true,
    val uniformLabels: UniformLabels = UniformLabels(),
    val fonnteToken: String = ""
)

class SettingsDataStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val SCHOOL_NAME = stringPreferencesKey("school_name")
        val ATTENDANCE_ENABLED = booleanPreferencesKey("attendance_enabled")
        val UNIFORM_LABELS_JSON = stringPreferencesKey("uniform_labels_json")
        val FONNTE_TOKEN = stringPreferencesKey("fonnte_token")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val labelsJson = prefs[Keys.UNIFORM_LABELS_JSON]
        val parsedLabels = if (!labelsJson.isNullOrBlank()) {
            runCatching { json.decodeFromString<UniformLabels>(labelsJson) }.getOrDefault(UniformLabels())
        } else {
            UniformLabels()
        }
        AppSettings(
            isDarkTheme = prefs[Keys.DARK_THEME] ?: false,
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
            schoolName = prefs[Keys.SCHOOL_NAME] ?: "SMK DIAN AL MUSTARIH",
            attendanceEnabled = prefs[Keys.ATTENDANCE_ENABLED] ?: true,
            uniformLabels = parsedLabels,
            fonnteToken = prefs[Keys.FONNTE_TOKEN] ?: ""
        )
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    }

    suspend fun setSchoolName(name: String) {
        context.dataStore.edit { it[Keys.SCHOOL_NAME] = name.trim().ifBlank { "SMK DIAN AL MUSTARIH" } }
    }

    suspend fun setAttendanceEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ATTENDANCE_ENABLED] = enabled }
    }

    suspend fun setUniformLabels(labels: UniformLabels) {
        context.dataStore.edit { it[Keys.UNIFORM_LABELS_JSON] = json.encodeToString(labels) }
    }

    suspend fun setFonnteToken(token: String) {
        context.dataStore.edit { it[Keys.FONNTE_TOKEN] = token.trim() }
    }
}
