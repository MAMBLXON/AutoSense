package com.mamblxon.autosense.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import com.mamblxon.autosense.R
import com.mamblxon.autosense.domain.model.ServiceItem
import com.mamblxon.autosense.ui.main.MainActivity
import com.mamblxon.autosense.ui.main.ServiceFilter
import com.mamblxon.autosense.ui.main.detail.DetailActivity

object NotificationHelper {

    const val CHANNEL_ID = "autosense_service_channel"
    const val CHANNEL_NAME = "Напоминания о ТО"
    const val CHANNEL_DESC = "Уведомления о приближающихся регламентных работах"
    const val NOTIFICATION_ID = 1001

    private const val REQUEST_DETAIL = 2001
    private const val REQUEST_MAIN = 2002
    private const val REQUEST_SNOOZE = 2003

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = CHANNEL_DESC }
        manager.createNotificationChannel(channel)
    }

    fun showServiceReminder(context: Context, urgentItems: List<ServiceItem>): Boolean {
        if (urgentItems.isEmpty()) return false
        ensureChannel(context)

        val mgr = NotificationManagerCompat.from(context)
        if (!mgr.areNotificationsEnabled()) return false

        val red = urgentItems.count { it.urgency == ServiceItem.URGENCY_RED }
        val yellow = urgentItems.count { it.urgency == ServiceItem.URGENCY_YELLOW }

        val title = when {
            red > 0 && yellow > 0 -> "ТО: $red просрочено, $yellow скоро"
            red > 0 -> "ТО: $red просрочено"
            else -> "ТО: $yellow приближается"
        }

        val summary = buildString {
            if (red > 0) append("🔴 $red требуют внимания. ")
            if (yellow > 0) append("🟡 $yellow приближаются. ")
        }.trim()

        val inbox = NotificationCompat.InboxStyle()
        urgentItems.take(5).forEach { item ->
            val dot = if (item.urgency == ServiceItem.URGENCY_RED) "🔴" else "🟡"
            inbox.addLine("$dot ${item.name} · ${item.car}")
        }
        if (urgentItems.size > 5) {
            inbox.setSummaryText("и ещё ${urgentItems.size - 5}")
        }

        // Основное действие — тап по уведомлению.
        val contentIntent = if (urgentItems.size == 1) {
            buildDetailPendingIntent(context, urgentItems.first().id)
        } else {
            buildMainPendingIntent(context, ServiceFilter.RED)
        }

        // Действие "Отложить" — работает по первой (самой срочной) позиции.
        // Для неё создаём отдельный PendingIntent с уникальным requestCode.
        val snoozeIntent = buildSnoozePendingIntent(context, urgentItems.first().id, days = 3)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(inbox)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .addAction(
                android.R.drawable.ic_menu_recent_history,
                "Отложить на 3 дня",
                snoozeIntent
            )
            .setAutoCancel(true)
            .build()

        return try {
            mgr.notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun buildDetailPendingIntent(context: Context, itemId: Long): PendingIntent {
        val detailIntent = Intent(context, DetailActivity::class.java).apply {
            putExtra(DetailActivity.EXTRA_ITEM_ID, itemId)
        }
        return TaskStackBuilder.create(context)
            .addNextIntentWithParentStack(detailIntent)
            .getPendingIntent(
                REQUEST_DETAIL,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ) ?: error("TaskStackBuilder.getPendingIntent returned null")
    }

    private fun buildMainPendingIntent(context: Context, filter: ServiceFilter): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_FILTER, filter.name)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_MAIN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildSnoozePendingIntent(
        context: Context,
        itemId: Long,
        days: Int
    ): PendingIntent {
        val intent = Intent(context, SnoozeReceiver::class.java).apply {
            putExtra(SnoozeReceiver.EXTRA_ITEM_ID, itemId)
            putExtra(SnoozeReceiver.EXTRA_DAYS, days)
        }
        // requestCode должен отличаться от других, чтобы система не перезаписала PendingIntent.
        // Используем itemId как уникальную часть.
        val requestCode = REQUEST_SNOOZE + itemId.toInt()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}