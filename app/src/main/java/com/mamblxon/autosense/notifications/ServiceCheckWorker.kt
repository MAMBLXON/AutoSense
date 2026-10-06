package com.mamblxon.autosense.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mamblxon.autosense.data.DefaultServiceRepository
import com.mamblxon.autosense.domain.model.ServiceItem
import com.mamblxon.autosense.domain.usecase.ServiceUseCase
import com.mamblxon.autosense.prefs.AppPrefs

class ServiceCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val repo = DefaultServiceRepository.get(applicationContext)
            val useCase = ServiceUseCase()
            val prefs = AppPrefs(applicationContext)

            val profileId = prefs.currentProfileId
            if (profileId <= 0L) return Result.success()

            val items = repo.getItemsOnce(profileId)
            if (items.isEmpty()) return Result.success()

            val threshold = prefs.yellowThreshold
            val now = System.currentTimeMillis()

            val recalculated = items.map { old ->
                old.copy(urgency = useCase.computeUrgency(old, now, threshold))
            }
            if (recalculated != items) repo.updateItems(recalculated)

            val urgent = recalculated
                .filter {
                    (it.urgency == ServiceItem.URGENCY_RED ||
                            it.urgency == ServiceItem.URGENCY_YELLOW) &&
                            it.snoozedUntilMillis <= now
                }
                .sortedByDescending { it.urgency }

            if (urgent.isNotEmpty() && prefs.notificationsEnabled) {
                // Для каждого элемента считаем «остаток» в памяти и передаём готовый
                // текст в NotificationHelper: «Через 500 км нужно заменить масло»
                // вместо абстрактного «Пора сделать ТО».
                val lines = urgent.map { item ->
                    useCase.formatRemaining(useCase.computeRemaining(item, now))
                }
                NotificationHelper.showServiceReminder(applicationContext, urgent, lines)
            }

            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "service_check_worker"
    }
}