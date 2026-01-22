package com.example.vssh

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getApiKey(): String = prefs.getString(KEY_API_KEY, "") ?: ""

    fun setApiKey(value: String) {
        prefs.edit().putString(KEY_API_KEY, value.trim()).apply()
    }

    fun getModel(): String = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL

    fun setModel(value: String) {
        val model = value.trim().ifEmpty { DEFAULT_MODEL }
        prefs.edit().putString(KEY_MODEL, model).apply()
    }

    fun getBaseUrl(): String = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL

    fun setBaseUrl(value: String) {
        val url = value.trim().ifEmpty { DEFAULT_BASE_URL }
        prefs.edit().putString(KEY_BASE_URL, url).apply()
    }

    companion object {
        private const val PREFS_NAME = "openrouter_settings"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_BASE_URL = "base_url"

        const val DEFAULT_MODEL = "openai/gpt-4o-mini"
        const val DEFAULT_BASE_URL = "https://openrouter.ai/api/v1"
    }
}
