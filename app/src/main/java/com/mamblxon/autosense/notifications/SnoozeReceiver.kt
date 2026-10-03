package com.mamblxon.autosense.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.mamblxon.autosense.data.DefaultServiceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Обрабатывает нажатие кнопки "Отложить" в уведомлении.
 *
 * Живёт вне Activity: система может доставить broadcast даже если
 * приложение в фоне или только что запущено. Поэтому работаем через
 * глобальный scope с SupervisorJob (одна упавшая операция не отменит остальные).
 */
class SnoozeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
        if (itemId <= 0) return

        val days = intent.getIntExtra(EXTRA_DAYS, DEFAULT_DAYS)
        val untilMillis = System.currentTimeMillis() + days * 24L * 60 * 60 * 1000

        val appContext = context.applicationContext

        // Спрячем уведомление сразу же — пользователь нажал "Отложить".
        NotificationManagerCompat.from(appContext).cancel(NotificationHelper.NOTIFICATION_ID)

        // Запускаем корутину для записи в БД. goAsync() продлевает жизнь receiver'а,
        // чтобы система не убила процесс раньше, чем корутина завершится.
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val repo = DefaultServiceRepository.get(appContext)
                repo.snoozeItem(itemId, untilMillis)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ITEM_ID = "extra_snooze_item_id"
        const val EXTRA_DAYS = "extra_snooze_days"
        const val DEFAULT_DAYS = 3
    }
}