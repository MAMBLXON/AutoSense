package com.mamblxon.autosense.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mamblxon.autosense.data.DefaultServiceRepository
import com.mamblxon.autosense.domain.model.ServiceItem
import com.mamblxon.autosense.domain.usecase.AdaptiveIntervalCalculator
import com.mamblxon.autosense.domain.usecase.AdaptiveResult
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

            // ── Адаптивный пайплайн (тот же, что в MainViewModel): история ОДИН раз, groupBy в памяти ──
            val allRecords = repo.getAllRecords()
            val historyByItem = allRecords.groupBy { it.serviceItemId }
            val calc = AdaptiveIntervalCalculator()
            val adaptiveOn = prefs.adaptiveModeEnabled

            val adaptedPairs = items.map { item ->
                val hist = historyByItem[item.id] ?: emptyList()
                val ad = if (adaptiveOn) calc.compute(item.intervalKm, item.intervalMonths, hist, now)
                         else AdaptiveResult(item.intervalKm, item.intervalMonths, false, "Адаптивный режим отключён.")
                val urg = useCase.computeUrgencyWithAdaptive(item, ad.recommendedKm, ad.recommendedMonths, now, threshold)
                val adaptedItem = item.copy(
                    urgency = urg,
                    intervalKm = ad.recommendedKm,
                    intervalMonths = ad.recommendedMonths
                )
                ad to adaptedItem
            }
            val urgencyById: Map<Long, Int> = items.mapIndexed { index, item -> item.id to adaptedPairs[index].second.urgency }.toMap()
            val adaptedItems = adaptedPairs.map { it.second }

            // В БД пишем только пересчитанный urgency; интервалы в базе остаются регламентными.
            val recalculated = items.map { old -> old.copy(urgency = urgencyById[old.id] ?: old.urgency) }
            if (recalculated != items) repo.updateItems(recalculated)

            val urgent = recalculated
                .filter {
                    (it.urgency == ServiceItem.URGENCY_RED ||
                            it.urgency == ServiceItem.URGENCY_YELLOW) &&
                            it.snoozedUntilMillis <= now
                }
                .sortedByDescending { it.urgency }

            if (urgent.isNotEmpty() && prefs.notificationsEnabled) {
                // Для каждого элемента считаем «остаток» по РЕКОМЕНДОВАННОМУ (адаптированному)
                // интервалу в памяти и передаём готовый текст в NotificationHelper:
                // «Через 500 км нужно заменить масло» вместо абстрактного «Пора сделать ТО».
                val lines = urgent.map { item ->
                    val adapted = adaptedItems.firstOrNull { it.id == item.id } ?: item
                    val baseLine = useCase.formatRemaining(useCase.computeRemaining(adapted, now))
                    val ad = adaptedPairs.firstOrNull { it.second.id == item.id }?.first
                    val suffix = if (ad != null && ad.isAdapted) " (адаптировано под ваш стиль)" else ""
                    "$baseLine$suffix"
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