package com.example.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppUserRole {
    NONE,
    THERAPIST,
    PATIENT;

    companion object {
        fun fromRaw(raw: String?): AppUserRole =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}

class AccessSessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getRole(uid: String): AppUserRole {
        if (uid.isBlank()) return AppUserRole.NONE
        return AppUserRole.fromRaw(prefs.getString("role_$uid", null))
    }

    fun setRole(uid: String, role: AppUserRole) {
        if (uid.isBlank()) return
        prefs.edit().putString("role_$uid", role.name).apply()
    }

    fun getRedeemedCode(uid: String): String? {
        if (uid.isBlank()) return null
        return prefs.getString("code_$uid", null)?.takeIf { it.isNotBlank() }
    }

    fun setRedeemedCode(uid: String, code: String?) {
        if (uid.isBlank()) return
        prefs.edit().apply {
            if (code.isNullOrBlank()) remove("code_$uid")
            else putString("code_$uid", code.trim().uppercase())
        }.apply()
    }

    fun getCompletedExercisesToday(code: String): Set<String> {
        if (code.isBlank()) return emptySet()
        val key = "done_${code}_${todayKey()}"
        return prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()
    }

    fun toggleExerciseCompletedToday(code: String, exerciseKey: String): Set<String> {
        if (code.isBlank()) return emptySet()
        val key = "done_${code}_${todayKey()}"
        val current = (prefs.getStringSet(key, emptySet()) ?: emptySet()).toMutableSet()
        if (!current.add(exerciseKey)) {
            current.remove(exerciseKey)
        }
        prefs.edit().putStringSet(key, current).apply()
        return current
    }

    fun hasPatientReminderOverride(code: String): Boolean {
        val norm = code.trim().uppercase()
        if (norm.isBlank()) return false
        return prefs.contains("pat_rem_override_$norm")
    }

    fun getPatientReminderEnabled(code: String, defaultValue: Boolean): Boolean {
        val norm = code.trim().uppercase()
        if (norm.isBlank() || !prefs.contains("pat_rem_override_$norm")) return defaultValue
        return prefs.getBoolean("pat_rem_enabled_$norm", defaultValue)
    }

    fun getPatientReminderHour(code: String, defaultValue: Int): Int {
        val norm = code.trim().uppercase()
        if (norm.isBlank() || !prefs.contains("pat_rem_override_$norm")) return defaultValue.coerceIn(0, 23)
        return prefs.getInt("pat_rem_hour_$norm", defaultValue).coerceIn(0, 23)
    }

    fun getPatientReminderMinute(code: String, defaultValue: Int): Int {
        val norm = code.trim().uppercase()
        if (norm.isBlank() || !prefs.contains("pat_rem_override_$norm")) return defaultValue.coerceIn(0, 59)
        return prefs.getInt("pat_rem_minute_$norm", defaultValue).coerceIn(0, 59)
    }

    fun savePatientReminderPrefs(code: String, enabled: Boolean, hour: Int, minute: Int) {
        val norm = code.trim().uppercase()
        if (norm.isBlank()) return
        prefs.edit()
            .putBoolean("pat_rem_override_$norm", true)
            .putBoolean("pat_rem_enabled_$norm", enabled)
            .putInt("pat_rem_hour_$norm", hour.coerceIn(0, 23))
            .putInt("pat_rem_minute_$norm", minute.coerceIn(0, 59))
            .apply()
    }

    fun clearUserSession(uid: String) {
        if (uid.isBlank()) return
        prefs.edit()
            .remove("role_$uid")
            .remove("code_$uid")
            .apply()
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private companion object {
        const val PREFS_NAME = "fonovida_access_prefs"
    }
}
