package com.mamblxon.autosense.ui.common

import android.text.InputType
import android.view.LayoutInflater
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import android.app.AlertDialog
import android.content.Context
import com.mamblxon.autosense.databinding.DialogEditRecordBinding
import com.mamblxon.autosense.domain.model.ServiceRecord
import com.mamblxon.autosense.domain.usecase.ServiceUseCase
import com.mamblxon.autosense.domain.usecase.ValidationResult
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Диалог редактирования записи истории ТО (ServiceRecord) — аналог showEditItemDialog,
 * но для конкретной выполненной работы: дата, пробег, стоимость, заметки.
 *
 * Валидация:
 *  • дата — не в будущем (MaterialDatePicker с constraints + повторная проверка UseCase);
 *  • пробег — целое число > 0;
 *  • стоимость — целое число >= 0 (пустая строка = 0).
 *
 * Кнопка «Сохранить» активна только когда вся форма валидна (живая валидация,
 * как в MainActivity.showEditItemDialog).
 */
object EditRecordDialog {

    fun show(
        context: Context,
        record: ServiceRecord,
        title: String = "Редактировать запись",
        useCase: ServiceUseCase = ServiceUseCase(),
        onSave: (record: ServiceRecord, newDateMillis: Long, newKm: Int, newCost: Int, newNote: String) -> Unit
    ) {
        val b = DialogEditRecordBinding.inflate(LayoutInflater.from(context))

        val dateFmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

        // Предзаполнение текущими значениями записи.
        var selectedDateMillis: Long = record.dateMillis
        b.etDate.setText(dateFmt.format(record.dateMillis))
        b.etKm.setText(record.km.toString())
        b.etCost.setText(if (record.cost > 0) record.cost.toString() else "")
        b.etNote.setText(record.note)

        // Поля ввода по ТЗ: пробег и стоимость — только цифры.
        b.etKm.inputType = InputType.TYPE_CLASS_NUMBER
        b.etCost.inputType = InputType.TYPE_CLASS_NUMBER

        // DatePicker: выбор даты тапом по полю; будущие даты заблокированы ограничениями.
        b.etDate.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Дата ТО")
                .setSelection(selectedDateMillis)
                .setCalendarConstraints(
                    CalendarConstraints.Builder()
                        .setValidator(DateOnlyDayValidator())
                        .build()
                )
                .build()
            picker.addOnPositiveButtonClickListener { selection ->
                // selection — полночь выбранного дня в UTC-миллисекундах;
                // извлекаем Y/M/D в UTC и сохраняем время суток исходной записи,
                // чтобы сортировка внутри дня не ломалась.
                val picked = Calendar.getInstance(
                    java.util.TimeZone.getTimeZone("UTC")
                ).apply { timeInMillis = selection }
                val merged = Calendar.getInstance().apply {
                    timeInMillis = selectedDateMillis
                    clear(java.util.Calendar.MILLISECOND)
                    set(
                        picked.get(Calendar.YEAR),
                        picked.get(Calendar.MONTH),
                        picked.get(Calendar.DAY_OF_MONTH)
                    )
                }
                // Если тронули сегодняшний день — не даём уйти в будущее по времени.
                if (merged.timeInMillis > System.currentTimeMillis()) {
                    merged.timeInMillis = System.currentTimeMillis()
                }
                selectedDateMillis = merged.timeInMillis
                b.etDate.setText(dateFmt.format(selectedDateMillis))
                b.tilDate.error = null
            }
            (context as? android.app.Activity)?.let { act ->
                picker.show(act.fragmentManager, "edit_record_date")
            }
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(b.root)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton("Отмена", null)
            .create()

        fun setError(layout: TextInputLayout, result: ValidationResult): Boolean {
            layout.error = if (result is ValidationResult.Invalid) result.error else null
            return result !is ValidationResult.Invalid
        }

        /**
         * Проверка «дата не в будущем» по календарным дням локального часового пояса.
         * MaterialDatePicker отдаёт полночь выбранного дня в UTC-миллисекундах,
         * поэтому сравниваем не instant-ы, а день/месяц/год — иначе запись за
         * «сегодня» в часовых поясах западнее UTC ошибочно считалась бы будущим.
         */
        fun isNotFutureLocalDay(millis: Long): Boolean {
            val picked = Calendar.getInstance().apply { timeInMillis = millis }
            val now = Calendar.getInstance()
            return !(
                picked.get(Calendar.YEAR) > now.get(Calendar.YEAR) ||
                    (picked.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                        picked.get(Calendar.DAY_OF_YEAR) > now.get(Calendar.DAY_OF_YEAR))
                )
        }

        /** Полная проверка формы; возвращает true, если всё валидно. */
        fun validateForm(): Boolean {
            var ok = true
            // Дата выбирается только через MaterialDatePicker с ограничением
            // «не больше сегодня», поэтому ошибка на поле даты не показывается —
            // проверка ниже служит страховкой от рассинхронизации значения.
            ok = isNotFutureLocalDay(selectedDateMillis) && ok
            b.tilDate.error = null
            ok = setError(
                b.tilKm,
                useCase.validateKm(
                    input = b.etKm.text?.toString().orEmpty(),
                    allowZero = false
                )
            ) && ok
            ok = setError(
                b.tilCost,
                useCase.validateRecordCost(b.etCost.text?.toString().orEmpty())
            ) && ok
            return ok
        }

        val afterTextChanged = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = validateForm()
            }
        }
        listOf(b.etDate, b.etKm, b.etCost, b.etNote).forEach { it.addTextChangedListener(afterTextChanged) }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = validateForm()

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                // Гвард на случай гонки: не сохраняем при невалидном вводе.
                if (!validateForm()) return@setOnClickListener

                val km = b.etKm.text?.toString()?.trim()?.toIntOrNull() ?: return@setOnClickListener
                val cost = b.etCost.text?.toString()?.trim()?.toIntOrNull() ?: 0
                val note = b.etNote.text?.toString()?.trim().orEmpty()

                onSave(record, selectedDateMillis, km, cost, note)
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}

/**
 * Ограничение MaterialDatePicker: запрещает выбор дат позже сегодняшнего дня.
 * Сравниваются календарные дни в UTC (selection у DatePicker — всегда полночь UTC),
 * а не instant-ы: иначе «завтрашняя» полночь в миллисекундах могла бы случайно
 * оказаться меньше текущего момента и пройти проверку.
 */
private class DateOnlyDayValidator : CalendarConstraints.DateValidator {

    override fun isValid(date: Long): Boolean = date <= utcMidnight(System.currentTimeMillis())

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: android.os.Parcel, flags: Int) { /* состояние не сериализуется */ }

    companion object {
        @JvmField
        val CREATOR: android.os.Parcelable.Creator<DateOnlyDayValidator> =
            object : android.os.Parcelable.Creator<DateOnlyDayValidator> {
                override fun createFromParcel(source: android.os.Parcel): DateOnlyDayValidator =
                    DateOnlyDayValidator()

                override fun newArray(size: Int): Array<DateOnlyDayValidator?> = arrayOfNulls(size)
            }

        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

        private fun utcMidnight(millis: Long): Long =
            Math.floorDiv(millis, DAY_MILLIS) * DAY_MILLIS
    }
}
