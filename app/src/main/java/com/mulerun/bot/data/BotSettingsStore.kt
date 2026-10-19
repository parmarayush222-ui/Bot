package com.mulerun.bot.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class BotProfile(
    val name: String,
    val apiKey: String,
    val provider: String = "NVIDIA NIM",
    val model: String = "meta/llama-3.1-8b-instruct",
    val baseUrl: String = "https://integrate.api.nvidia.com/v1",
)

data class BotSettings(
    val activeProfile: String = "Me",
    val wakeWordEnabled: Boolean = false,
    val screenAwarenessEnabled: Boolean = false,
    val includeScreenInAi: Boolean = false,
    val voiceName: String? = null,
    val relationship: String = "Mentor + friend",
)

class BotSettingsStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "bot_secure_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun settings(): BotSettings = BotSettings(
        activeProfile = prefs.getString("active_profile", "Me") ?: "Me",
        wakeWordEnabled = prefs.getBoolean("wake_word", false),
        screenAwarenessEnabled = prefs.getBoolean("screen_awareness", false),
        includeScreenInAi = prefs.getBoolean("include_screen_ai", false),
        voiceName = prefs.getString("voice_name", null),
        relationship = prefs.getString("relationship", "Mentor + friend") ?: "Mentor + friend",
    )

    fun profiles(): List<BotProfile> {
        val names = prefs.getStringSet("profiles", setOf("Me")) ?: setOf("Me")
        return names.sorted().map { name ->
            BotProfile(
                name = name,
                apiKey = prefs.getString(keyFor(name), "") ?: "",
                provider = prefs.getString(metaKey(name, "provider"), "NVIDIA NIM") ?: "NVIDIA NIM",
                model = prefs.getString(metaKey(name, "model"), "meta/llama-3.1-8b-instruct") ?: "meta/llama-3.1-8b-instruct",
                baseUrl = prefs.getString(metaKey(name, "base_url"), "https://integrate.api.nvidia.com/v1") ?: "https://integrate.api.nvidia.com/v1",
            )
        }
    }

    fun activeProfile(): BotProfile {
        val active = settings().activeProfile
        return profiles().firstOrNull { it.name == active } ?: BotProfile(
            name = active,
            apiKey = "",
            provider = "NVIDIA NIM",
            model = "meta/llama-3.1-8b-instruct",
            baseUrl = "https://integrate.api.nvidia.com/v1",
        )
    }

    fun saveProfile(name: String, apiKey: String, provider: String, model: String, baseUrl: String) {
        val cleanName = name.trim().ifBlank { "Me" }
        val names = (prefs.getStringSet("profiles", emptySet()) ?: emptySet()).toMutableSet()
        names += cleanName
        prefs.edit()
            .putStringSet("profiles", names)
            .putString(keyFor(cleanName), apiKey.trim())
            .putString(metaKey(cleanName, "provider"), provider)
            .putString(metaKey(cleanName, "model"), model.trim())
            .putString(metaKey(cleanName, "base_url"), baseUrl.trim().trimEnd('/'))
            .putString("active_profile", cleanName)
            .apply()
    }

    fun selectProfile(name: String) = prefs.edit().putString("active_profile", name).apply()

    fun update(
        wakeWordEnabled: Boolean? = null,
        screenAwarenessEnabled: Boolean? = null,
        includeScreenInAi: Boolean? = null,
        voiceName: String? = settings().voiceName,
        relationship: String? = settings().relationship,
    ) {
        prefs.edit().apply {
            wakeWordEnabled?.let { putBoolean("wake_word", it) }
            screenAwarenessEnabled?.let { putBoolean("screen_awareness", it) }
            includeScreenInAi?.let { putBoolean("include_screen_ai", it) }
            if (voiceName == null) remove("voice_name") else putString("voice_name", voiceName)
            relationship?.let { putString("relationship", it) }
        }.apply()
    }

    private fun keyFor(profile: String): String = "api_key_${profile.hashCode()}"
    private fun metaKey(profile: String, field: String): String = "profile_${profile.hashCode()}_$field"
}
