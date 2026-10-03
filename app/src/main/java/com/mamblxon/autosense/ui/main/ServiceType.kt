package com.mamblxon.autosense.ui.main

/**
 * Типы работ для фильтрации списка ТО (диалог «Фильтр» с чекбоксами).
 * Совпадение — по названию работы или категории (без учёта регистра),
 * потому что в каталоге «Масло» живёт в категории «Двигатель», а «Шины»
 * могут быть вообще не разнесены по категориям.
 */
enum class ServiceType(val label: String, val keywords: List<String>) {
    OIL("Масло", listOf("масл")),                       // моторное масло, масло МКПП/АКПП, тормозная жидкость и т.п.
    BRAKES("Тормоза", listOf("тормоз")),                // колодки, диски, жидкость
    TIRES("Шины", listOf("шин", "колес", "колосс", "покрыш", "дисков", "балансировк")),
    FILTERS("Фильтры", listOf("фильтр")),               // масляный, воздушный, салонный, топливный
    OTHER("Другое", emptyList());                       // всё, что не попало ни под один тип

    /** Тип работы по её названию и категории. OTHER — если ни один ключ не совпал. */
    fun matches(name: String, category: String): Boolean {
        if (this == OTHER) return values().firstOrNull { it != OTHER && it.matchesRaw(name, category) } == null
        return matchesRaw(name, category)
    }

    private fun matchesRaw(name: String, category: String): Boolean {
        val haystack = "$name $category".lowercase()
        return keywords.any { haystack.contains(it) }
    }

    companion object {
        /** Все типы, кроме OTHER (для чекбоксов диалога фильтра). */
        val SELECTABLE: List<ServiceType> = values().filter { it != OTHER }
    }
}
