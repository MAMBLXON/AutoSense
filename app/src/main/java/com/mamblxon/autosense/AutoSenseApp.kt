package com.mamblxon.autosense

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.mamblxon.autosense.prefs.AppPrefs

class AutoSenseApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val mode = AppPrefs(this).themeMode
        val delegateMode = when (mode) {
            AppPrefs.MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppPrefs.MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(delegateMode)
    }
}