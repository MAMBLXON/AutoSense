package com.mamblxon.autosense.export

import com.mamblxon.autosense.domain.model.ServiceRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    private const val SEPARATOR = ","
    private const val LINE_END = "\r\n" // CRLF — совместимо с Excel и LibreOffice

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun buildCsv(records: List<ServiceRecord>): String {
        val sb = StringBuilder()

        // Заголовок
        sb.append("id")
            .append(SEPARATOR).append("serviceItemId")
            .append(SEPARATOR).append("date")
            .append(SEPARATOR).append("km")
            .append(SEPARATOR).append("note")
            .append(LINE_END)

        // Данные
        records.forEach { r ->
            sb.append(r.id)
                .append(SEPARATOR).append(r.serviceItemId)
                .append(SEPARATOR).append(dateFmt.format(Date(r.dateMillis)))
                .append(SEPARATOR).append(r.km)
                .append(SEPARATOR).append(escape(r.note))
                .append(LINE_END)
        }

        return sb.toString()
    }

    /**
     * Экранирование поля по RFC 4180.
     * Если внутри есть запятая, кавычка или перевод строки — оборачиваем в кавычки,
     * а внутренние кавычки удваиваем.
     */
    private fun escape(value: String): String {
        val needsQuotes = value.contains(SEPARATOR) ||
                value.contains('"') ||
                value.contains('\n') ||
                value.contains('\r')
        if (!needsQuotes) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}