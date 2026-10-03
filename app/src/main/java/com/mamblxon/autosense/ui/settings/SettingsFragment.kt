package com.mamblxon.autosense.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.mamblxon.autosense.R
import com.mamblxon.autosense.data.DefaultServiceRepository
import com.mamblxon.autosense.notifications.WorkScheduler
import com.mamblxon.autosense.prefs.AppPrefs
import kotlinx.coroutines.launch

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val prefs = AppPrefs(requireContext())

        // --- Тема ---
        val themePref = findPreference<ListPreference>(AppPrefs.KEY_THEME_MODE)
        themePref?.setOnPreferenceChangeListener { _, newValue ->
            val mode = (newValue as? String)?.toIntOrNull() ?: AppPrefs.MODE_SYSTEM
            prefs.themeMode = mode
            applyThemeMode(mode)
            true
        }

        // --- Порог жёлтого ---
        val thresholdPref = findPreference<SeekBarPreference>(AppPrefs.KEY_YELLOW_THRESHOLD)
        if (thresholdPref != null) {
            thresholdPref.value =
                ((prefs.yellowThreshold - 0.5f) / 0.49f * 49f).toInt().coerceIn(0, 49)
            thresholdPref.setOnPreferenceChangeListener { _, newValue ->
                val steps = (newValue as? Int) ?: return@setOnPreferenceChangeListener false
                val threshold = 0.5f + (steps / 49f) * 0.49f
                prefs.yellowThreshold = threshold
                // Пересчитываем статусы с новым порогом в фоне.
                WorkScheduler.runOnceNow(requireContext().applicationContext)
                true
            }
        }

        // --- Период проверки ---
        val periodPref = findPreference<ListPreference>(AppPrefs.KEY_PERIOD_HOURS)
        periodPref?.setOnPreferenceChangeListener { _, newValue ->
            val hours = (newValue as? String)?.toIntOrNull() ?: 24
            prefs.checkPeriodHours = hours
            WorkScheduler.schedulePeriodic(requireContext().applicationContext)
            Toast.makeText(
                requireContext(),
                "Период проверки: $hours ч",
                Toast.LENGTH_SHORT
            ).show()
            true
        }

        // --- Уведомления вкл/выкл ---
        val notifPref = findPreference<SwitchPreferenceCompat>(AppPrefs.KEY_NOTIF_ENABLED)
        notifPref?.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as? Boolean ?: true
            prefs.notificationsEnabled = enabled
            if (!enabled) {
                Toast.makeText(
                    requireContext(),
                    "Уведомления отключены",
                    Toast.LENGTH_SHORT
                ).show()
            }
            true
        }

        // --- Сброс данных ---
        val resetPref = findPreference<Preference>("action_reset")
        resetPref?.setOnPreferenceClickListener {
            confirmReset()
            true
        }
    }

    private fun applyThemeMode(mode: Int) {
        val delegateMode = when (mode) {
            AppPrefs.MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppPrefs.MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(delegateMode)
    }

    private fun confirmReset() {
        AlertDialog.Builder(requireContext())
            .setTitle("Сбросить все данные?")
            .setMessage("Будут удалены все задачи ТО и вся история. Действие необратимо.")
            .setPositiveButton("Удалить") { _, _ -> resetAllData() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun resetAllData() {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val repo = DefaultServiceRepository.get(appContext)
            repo.resetAll()
            // После сброса заново сгенерируем items для текущей машины
            // при следующем запуске MainActivity (там есть seedIfEmpty).
            Toast.makeText(
                appContext,
                "Все данные удалены. Перезапусти приложение.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}