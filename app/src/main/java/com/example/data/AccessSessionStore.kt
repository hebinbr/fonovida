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
