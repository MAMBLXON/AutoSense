package com.mamblxon.autosense.domain.usecase

import com.mamblxon.autosense.domain.model.ServiceRecord
import java.util.Calendar

data class AdaptiveResult(
    val recommendedKm: Int,
    val recommendedMonths: Int,
    val isAdapted: Boolean,
    val reason: String
)

class AdaptiveIntervalCalculator {

    fun compute(
        baseKm: Int,
        baseMonths: Int,
        history: List<ServiceRecord>,
        nowMillis: Long = System.currentTimeMillis()
    ): AdaptiveResult {
        var recKm = baseKm
        var recMonths = baseMonths
        var adapted = false
        val reasons = mutableListOf<String>()

        val sorted = history.sortedBy { it.dateMillis }
        if (sorted.size >= 2 && baseKm > 0) {
            val deltas = sorted.zipWithNext { a, b -> b.km - a.km }.filter { it > 0 }
            if (deltas.isNotEmpty()) {
                val avg = deltas.average()
                val ratio = (avg / baseKm).toFloat()
                val clamped = ratio.coerceIn(0.7f, 1.1f)
                if (clamped < 0.95f) {
                    recKm = roundToHundreds((baseKm * clamped).toInt())
                    adapted = true
                    reasons.add("Средний фактический интервал ${avg.toInt()} км ниже регламента $baseKm км — условия эксплуатации тяжёлые (город/агрессивная езда).")
                } else if (clamped > 1.05f) {
                    val upper = (baseKm * 1.1f).toInt()
                    recKm = roundToHundreds((baseKm * clamped).toInt().coerceAtMost(upper))
                    adapted = true
                    reasons.add("Фактический интервал выше регламента — допускается увеличение до $recKm км.")
                }
            }
        }

        if (baseMonths > 0) {
            val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
            val month = cal.get(Calendar.MONTH) + 1
            val winter = month == 11 || month == 12 || month == 1 || month == 2 || month == 3
            if (winter) {
                val winterMonths = (baseMonths * 0.85f).toInt().coerceAtLeast(1)
                if (winterMonths != baseMonths) {
                    recMonths = winterMonths
                    adapted = true
                    reasons.add("Зимний период: временной интервал сокращён до $recMonths мес.")
                }
            }
        }

        val reason = if (reasons.isEmpty()) "Рекомендуется следовать регламенту производителя."
                     else reasons.joinToString(" ")

        return AdaptiveResult(recKm, recMonths, adapted, reason)
    }

    private fun roundToHundreds(v: Int): Int = (v / 100) * 100
}
