package com.mamblxon.autosense.ui.common

import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.mamblxon.autosense.R
import com.mamblxon.autosense.databinding.DialogInputBinding
import com.mamblxon.autosense.databinding.DialogMarkDoneBinding

object AppDialogs {

    // =========================================================
    // Snackbar / Toast
    // =========================================================

    fun toast(context: Context, text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun snackbar(
        view: View,
        text: String,
        isError: Boolean = false,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null
    ) {
        val context = view.context
        val bar = Snackbar.make(view, text, Snackbar.LENGTH_LONG)

        val bg = if (isError) ContextCompat.getColor(context, R.color.error)
        else ContextCompat.getColor(context, R.color.primary)
        val fg = ContextCompat.getColor(context, R.color.on_primary)

        bar.setBackgroundTint(bg)
        bar.setTextColor(fg)
        if (actionLabel != null) {
            bar.setActionTextColor(fg)
            bar.setAction(actionLabel) { onAction?.invoke() }
        }
        bar.show()
    }

    fun snackbarError(
        view: View,
        text: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null
    ) = snackbar(view, text, isError = true, actionLabel = actionLabel, onAction = onAction)

    fun snackbarInfo(
        view: View,
        text: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null
    ) = snackbar(view, text, isError = false, actionLabel = actionLabel, onAction = onAction)

    /**
     * Snackbar с действием (например «Отменить» для свайп-удаления).
     * durationMs — время показа (по умолчанию 4 секунды).
     */
    fun snackbarWithAction(
        view: View,
        text: String,
        actionLabel: String,
        durationMs: Int = 4000,
        isError: Boolean = false,
        onAction: () -> Unit
    ) {
        val context = view.context
        val bar = Snackbar.make(view, text, durationMs)

        val bg = if (isError) ContextCompat.getColor(context, R.color.error)
        else ContextCompat.getColor(context, R.color.primary)
        bar.setBackgroundTint(bg)
        bar.setTextColor(ContextCompat.getColor(context, R.color.on_primary))
        bar.setActionTextColor(ContextCompat.getColor(context, R.color.on_primary))
        bar.setAction(actionLabel) { onAction() }
        bar.show()
    }

    // =========================================================
    // Универсальный диалог с одним полем
    // =========================================================

    fun showInput(
        context: Context,
        title: String,
        message: String?,
        hint: String,
        initialValue: String = "",
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        iconRes: Int = android.R.drawable.ic_menu_edit,
        positiveText: String = "Сохранить",
        negativeText: String = "Отмена",
        validate: ((String) -> String?)? = null,
        onConfirm: (String) -> Unit
    ) {
        val binding = DialogInputBinding.inflate(LayoutInflater.from(context))
        binding.icon.setImageResource(iconRes)
        binding.tilInput.hint = hint
        binding.etInput.inputType = inputType
        binding.etInput.setText(initialValue)
        binding.etInput.setSelection(initialValue.length)

        if (message.isNullOrBlank()) {
            binding.message.visibility = View.GONE
        } else {
            binding.message.text = message
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(binding.root)
            .setPositiveButton(positiveText, null)
            .setNegativeButton(negativeText, null)
            .create()

        dialog.setOnShowListener {
            val btn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            btn.setOnClickListener {
                val value = binding.etInput.text?.toString()?.trim().orEmpty()
                val error = validate?.invoke(value)
                if (error != null) {
                    binding.tilInput.error = error
                    return@setOnClickListener
                }
                binding.tilInput.error = null
                dialog.dismiss()
                onConfirm(value)
            }
        }

        dialog.show()
    }

    // =========================================================
    // Диалог «Отметить ТО выполненным» — 3 поля
    // =========================================================

    fun showMarkDoneDialog(
        context: Context,
        serviceName: String,
        carDisplayName: String,
        initialKm: Int,
        lastServiceKm: Int,
        currentProfileKm: Int = 0,
        onConfirm: (km: Int, cost: Int, note: String, updateProfileKm: Boolean) -> Unit
    ) {
        val binding = DialogMarkDoneBinding.inflate(LayoutInflater.from(context))

        binding.message.text = buildString {
            append(serviceName)
            if (carDisplayName.isNotBlank()) {
                append(" · ").append(carDisplayName)
            }
        }
        binding.etKm.setText(initialKm.toString())

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Отметить ТО")
            .setView(binding.root)
            .setPositiveButton("Отметить", null)
            .setNegativeButton("Отмена", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val kmText = binding.etKm.text?.toString()?.trim().orEmpty()
                    val costText = binding.etCost.text?.toString()?.trim().orEmpty()
                    val note = binding.etNote.text?.toString()?.trim().orEmpty()

                    val km = kmText.toIntOrNull()
                    val cost = if (costText.isBlank()) 0 else costText.toIntOrNull()

                    var valid = true

                    when {
                        km == null -> {
                            binding.tilKm.error = "Введи целое число"
                            valid = false
                        }
                        km < 0 -> {
                            binding.tilKm.error = "Пробег не может быть отрицательным"
                            valid = false
                        }
                        km > 2_000_000 -> {
                            binding.tilKm.error = "Слишком большое значение"
                            valid = false
                        }
                        km < lastServiceKm -> {
                            binding.tilKm.error = "Не меньше $lastServiceKm км"
                            valid = false
                        }
                        else -> binding.tilKm.error = null
                    }

                    if (cost == null) {
                        binding.tilCost.error = "Стоимость должна быть числом"
                        valid = false
                    } else if (cost < 0 || cost > 10_000_000) {
                        binding.tilCost.error = "Некорректная сумма"
                        valid = false
                    } else {
                        binding.tilCost.error = null
                    }

                    if (!valid) return@setOnClickListener

                    val kmValue = km!!
                    val costValue = cost!!

                    // КРИТИЧЕСКАЯ ПРОВЕРКА: пробег не должен превышать текущий пробег профиля.
                    // currentProfileKm <= 0 — одометр автомобиля неизвестен, блокировки нет.
                    if (currentProfileKm > 0 && kmValue > currentProfileKm) {
                        MaterialAlertDialogBuilder(context)
                            .setTitle("Пробег больше текущего")
                            .setMessage(
                                "Введённый пробег $kmValue км больше текущего пробега " +
                                    "автомобиля ($currentProfileKm км). Отметить как новый текущий пробег?"
                            )
                            .setPositiveButton("Да") { _, _ ->
                                dialog.dismiss()
                                onConfirm(kmValue, costValue, note, true)
                            }
                            .setNegativeButton("Нет") { confirmDialog, _ ->
                                confirmDialog.dismiss()
                                toast(
                                    context,
                                    "Введите пробег не больше $currentProfileKm км"
                                )
                            }
                            .show()
                        return@setOnClickListener
                    }

                    dialog.dismiss()
                    onConfirm(kmValue, costValue, note, false)
                }
        }

        dialog.show()
    }

    // =========================================================
    // Простые диалоги
    // =========================================================

    fun showConfirm(
        context: Context,
        title: String,
        message: String,
        positiveText: String = "OK",
        negativeText: String = "Отмена",
        destructive: Boolean = false,
        onConfirm: () -> Unit
    ) {
        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveText) { _, _ -> onConfirm() }
            .setNegativeButton(negativeText, null)

        if (destructive) {
            val dialog = builder.create()
            dialog.setOnShowListener {
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(ContextCompat.getColor(context, R.color.error))
            }
            dialog.show()
        } else {
            builder.show()
        }
    }

    fun showSingleChoice(
        context: Context,
        title: String,
        options: Array<String>,
        checkedIndex: Int,
        onPick: (Int) -> Unit
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setSingleChoiceItems(options, checkedIndex) { d, which ->
                d.dismiss()
                onPick(which)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    fun showActions(
        context: Context,
        title: String,
        actions: Array<String>,
        onPick: (Int) -> Unit
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setItems(actions) { _, which -> onPick(which) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    /**
     * Диалог с чекбоксами (несколько вариантов одновременно).
     * onApply — текущее состояние чекбоксов при «Применить», onReset — при «Сбросить».
     */
    fun showMultiChoice(
        context: Context,
        title: String,
        items: Array<String>,
        checked: BooleanArray,
        positiveText: String = "Применить",
        negativeText: String? = null,
        onApply: (BooleanArray) -> Unit,
        onReset: (() -> Unit)? = null
    ) {
        val state = checked.clone()
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMultiChoiceItems(items, state) { _, which, isChecked ->
                state[which] = isChecked
            }
            .setPositiveButton(positiveText, null)
            .apply {
                if (negativeText != null) {
                    setNegativeButton(negativeText) { _, _ -> onReset?.invoke() }
                } else {
                    setNeutralButton("Отмена", null)
                }
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    dialog.dismiss()
                    onApply(state.clone())
                }
        }
        dialog.show()
    }
}