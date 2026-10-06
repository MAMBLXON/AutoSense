package com.mamblxon.autosense.ui.main.add

import android.os.Bundle
import android.text.InputFilter
import android.text.Spanned
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.CompositeDateValidator
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputLayout
import com.mamblxon.autosense.databinding.ActivityAddBinding
import com.mamblxon.autosense.domain.usecase.ServiceUseCase
import com.mamblxon.autosense.domain.usecase.ValidationResult
import com.mamblxon.autosense.ui.common.AppDialogs
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Экран добавления новой работы (ТО).
 * Открывается по кнопке FAB и по кнопке «Добавить первую запись»
 * на заглушке пустого списка в MainActivity.
 */
class AddActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddBinding
    private val viewModel: AddViewModel by viewModels()
    private val useCase = ServiceUseCase()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        // Автоформат ДД.ММ.ГГГГ + выбор даты тапом по иконке календаря
        binding.etDate.filters = arrayOf(InputFilter.LengthFilter(10), DateMaskInputFilter())
        binding.tilDate.setEndIconOnClickListener { showDatePicker() }

        // Живая валидация: при каждом изменении пересчитываем canSave
        listOf(binding.etName, binding.etIntervalKm, binding.etIntervalMonths, binding.etDate, binding.etCost)
            .forEach { it.doAfterTextChanged { pushForm() } }

        binding.btnSave.setOnClickListener { attemptSave() }

        // repeatOnLifecycle(STARTED): подписка отменяется на STOP и
        // перезапускается после пересоздания Activity (поворот экрана),
        // поэтому результат сохранения (isSaving/canSave/сообщение) доходит
        // до нового экземпляра Activity, а не теряется в мёртвой корутине.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest { st ->
                    binding.tvCarInfo.text = if (st.carName.isBlank()) {
                        "Новая работа"
                    } else {
                        "${st.carName} · текущий пробег ${st.currentKm} км"
                    }
                    // Кнопка «Сохранить» неактивна (серая), пока форма не полностью валидна
                    binding.btnSave.isEnabled = st.canSave && !st.isSaving
                }
            }
        }

        pushForm()
    }

    private fun currentForm() = AddForm(
        name = binding.etName.text?.toString().orEmpty(),
        intervalKm = binding.etIntervalKm.text?.toString().orEmpty(),
        intervalMonths = binding.etIntervalMonths.text?.toString().orEmpty(),
        lastDate = binding.etDate.text?.toString().orEmpty(),
        cost = binding.etCost.text?.toString().orEmpty()
    )

    private fun pushForm() {
        viewModel.onFormChanged(currentForm())
    }

    /** Проверка всех полей с подсветкой красным + текст ошибки под полем. */
    private fun attemptSave() {
        val form = currentForm()
        var valid = true

        valid = showError(binding.tilName, useCase.validateName(form.name)) && valid

        val kmResult = useCase.validateIntervalKm(form.intervalKm, viewModel.state.value.currentKm)
        val monthsResult = useCase.validateIntervalMonths(form.intervalMonths)
        valid = showError(binding.tilIntervalKm, kmResult) && valid
        valid = showError(binding.tilIntervalMonths, monthsResult) && valid
        if (kmResult.isValid && monthsResult.isValid &&
            !useCase.hasPositiveInterval(form.intervalKm, form.intervalMonths)
        ) {
            binding.tilIntervalKm.error = "Хотя бы один интервал > 0"
            binding.tilIntervalMonths.error = "Хотя бы один интервал > 0"
            valid = false
        }

        if (form.lastDate.isNotBlank()) {
            valid = showError(binding.tilDate, useCase.validateDateNotFuture(form.lastDate)) && valid
        } else {
            binding.tilDate.error = null
        }

        if (form.cost.isNotBlank()) {
            valid = showError(binding.tilCost, useCase.validateCost(form.cost)) && valid
        } else {
            binding.tilCost.error = null
        }

        if (!valid) return

        viewModel.save { outcome ->
            when (outcome) {
                is SaveOutcome.Success -> {
                    AppDialogs.toast(this, "Запись добавлена")
                    finish()
                }
                is SaveOutcome.Failure -> {
                    Log.e("AutoSense", "AddActivity save failed: ${outcome.message}")
                    AppDialogs.snackbarError(view = binding.root, text = outcome.message)
                }
            }
        }
    }

    /** Диалог выбора даты; будущие даты заблокированы (валидация 1В). */
    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Дата последнего ТО")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .setCalendarConstraints(
                CalendarConstraints.Builder()
                    .setValidator(
                        CompositeDateValidator.allOf(
                            listOf(DateValidatorPointBackward.now())
                        )
                    )
                    .build()
            )
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            // UTC-миллисекунды от MaterialDatePicker → локальная дата в поле.
            binding.etDate.setText(
                SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(java.util.Date(millis))
            )
        }
        picker.show(supportFragmentManager, "add_date_picker")
    }

    /** true — поле валидно; false — показана ошибка. */
    private fun showError(layout: TextInputLayout, result: ValidationResult): Boolean {
        return when (result) {
            is ValidationResult.Invalid -> {
                layout.error = result.error
                false
            }
            ValidationResult.Valid -> {
                layout.error = null
                true
            }
        }
    }

    /**
     * Масочный фильтр: оставляет только цифры и автоматически вставляет
     * точки после ДД и ММ → формат ДД.ММ.ГГГГ.
     */
    private class DateMaskInputFilter : InputFilter {
        override fun filter(
            source: CharSequence, start: Int, end: Int,
            dest: Spanned, dstart: Int, dend: Int
        ): CharSequence {
            val old = dest.toString()

            // Считаем «цифровую» версию старого текста и позицию вставки/удаления в ней.
            val digitsFromOld = old.filter { it.isDigit() }.toMutableList()
            val digitPosStart = old.take(dstart).count { it.isDigit() }
            val digitPosEnd = old.take(dend.coerceAtMost(old.length)).count { it.isDigit() }

            val insertedDigits = source.substring(start, end).filter { it.isDigit() }
            for (i in digitPosStart until digitPosEnd) digitsFromOld.removeAt(digitPosStart)
            digitsFromOld.addAll(digitPosStart, insertedDigits.toList())

            val digits = digitsFromOld.joinToString("").take(8)

            val formatted = StringBuilder().apply {
                for (i in digits.indices) {
                    if (i == 2 || i == 4) append('.')
                    append(digits[i])
                }
            }.toString()

            return if (formatted == old) "" else formatted
        }
    }
}
