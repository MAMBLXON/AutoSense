package com.mamblxon.autosense.ui.main.detail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mamblxon.autosense.R
import com.mamblxon.autosense.databinding.ActivityDetailBinding
import com.mamblxon.autosense.databinding.DialogEditItemBinding
import com.mamblxon.autosense.export.CsvExporter
import com.mamblxon.autosense.domain.model.ServiceItem
import com.mamblxon.autosense.ui.common.AppDialogs
import com.mamblxon.autosense.ui.common.EditRecordDialog
import com.mamblxon.autosense.domain.model.ServiceRecord
import com.mamblxon.autosense.ui.main.UiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private val adapter = ServiceRecordAdapter(
        onEditRequest = { record -> showEditRecordDialog(record) }
    )

    private var itemId: Long = -1L
    private var lastCsv: String? = null
    private var finished = false

    /** Доступ к пробегу профиля (resolveKnownKm / setLastKm) для проверки при отметке ТО. */
    private val prefs by lazy { com.mamblxon.autosense.prefs.AppPrefs(this) }

    private val viewModel: DetailViewModel by viewModels {
        DetailViewModel.Factory(application, itemId)
    }

    private val createCsvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) writeCsvToUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)

        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = adapter

        binding.btnMarkDone.isEnabled = false
        binding.btnMarkDone.setOnClickListener { showMarkDoneDialog() }

        // repeatOnLifecycle(STARTED): без утечки корутин при пересоздании Activity
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.messages.collect { msg ->
                        when (msg) {
                            is UiMessage.Info -> AppDialogs.toast(this@DetailActivity, msg.text)
                            is UiMessage.Error -> AppDialogs.snackbarError(
                                view = binding.root,
                                text = msg.text
                            )
                        }
                    }
                }

                viewModel.state.collectLatest { st ->
                    if (st.notFound && !finished) {
                        finished = true
                        AppDialogs.toast(this@DetailActivity, "Позиция удалена")
                        finish()
                        return@collectLatest
                    }

                    binding.toolbar.title = st.item?.car ?: "AutoSense"
                    binding.tvTitle.text = st.item?.name ?: "—"
                    binding.tvSubtitle.text = st.item?.let {
                        val km = it.intervalKm.toString().reversed()
                            .chunked(3).joinToString(" ").reversed()
                        "${it.car} · каждые $km км / ${it.intervalMonths} мес"
                    } ?: ""

                    // Блок «Прогноз следующего ТО»: скрыт, если прогноз пустой
                    // (например, оба интервала обнулены).
                    binding.forecastCard.isVisible = st.forecastText.isNotBlank()
                    binding.tvForecast.text = st.forecastText
                    binding.tvForecastRemaining.text = st.remainingText
                    binding.tvForecastRemaining.isVisible = st.remainingText.isNotBlank()
                    st.item?.let { item ->
                        val colorRes = when (item.urgency) {
                            ServiceItem.URGENCY_RED -> R.color.urgency_red
                            ServiceItem.URGENCY_YELLOW -> R.color.urgency_yellow
                            else -> R.color.urgency_green
                        }
                        val color = ContextCompat.getColor(this@DetailActivity, colorRes)
                        binding.tvForecast.setTextColor(color)
                        binding.tvForecastRemaining.setTextColor(color)
                    }

                    adapter.submitList(st.history)
                    binding.tvEmpty.isVisible = st.history.isEmpty()
                    binding.btnMarkDone.isEnabled = st.item != null && !st.isLoading
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val newId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
        if (newId != itemId && newId > 0) {
            finish()
            startActivity(intent)
        }
    }

    // =========================================================
    // Меню
    // =========================================================

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_edit -> {
                showEditDialog()
                true
            }
            R.id.action_export -> {
                startExport()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // =========================================================
    // Экспорт CSV
    // =========================================================

    private fun startExport() {
        val history = viewModel.state.value.history
        if (history.isEmpty()) {
            AppDialogs.snackbarInfo(
                view = binding.root,
                text = "Нечего экспортировать"
            )
            return
        }
        lastCsv = CsvExporter.buildCsv(history)
        val suggestedName = "autosense_${itemId}_${System.currentTimeMillis()}.csv"
        createCsvLauncher.launch(suggestedName)
    }

    private fun writeCsvToUri(uri: Uri) {
        val csv = lastCsv ?: return
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(csv.toByteArray(Charsets.UTF_8))
                        out.flush()
                    } ?: error("openOutputStream returned null")
                }
            }
            result.onSuccess {
                AppDialogs.toast(this@DetailActivity, "Экспорт завершён")
            }.onFailure { t ->
                AppDialogs.snackbarError(
                    view = binding.root,
                    text = "Ошибка экспорта: ${t.message}"
                )
            }
        }
    }

    // =========================================================
    // Отметить ТО выполненным
    // =========================================================

    private fun showMarkDoneDialog() {
        val item = viewModel.state.value.item ?: return
        val useCase = com.mamblxon.autosense.domain.usecase.ServiceUseCase()

        // Актуальный пробег автомобиля: из prefs (последний ввод), fallback — currentKm работы.
        val currentProfileKm = prefs.resolveKnownKm(prefs.currentProfileId, item.currentKm)

        AppDialogs.showInput(
            context = this,
            title = "Отметить ТО выполненным",
            message = "${item.name} · ${item.car}",
            hint = "Текущий пробег, км",
            initialValue = item.currentKm.toString(),
            inputType = InputType.TYPE_CLASS_NUMBER,
            iconRes = android.R.drawable.checkbox_on_background,
            positiveText = "Отметить",
            validate = { value ->
                // upperBound — текущий пробег профиля: подсказка под полем, если km больше одометра.
                val bound = if (currentProfileKm > 0 && currentProfileKm >= item.currentKm) currentProfileKm else 0
                when (val r = useCase.validateKm(value, lastServiceKm = item.currentKm, upperBound = bound)) {
                    is com.mamblxon.autosense.domain.usecase.ValidationResult.Invalid -> r.error
                    else -> null
                }
            },
            onConfirm = { value ->
                val km = value.toInt()
                if (currentProfileKm > 0 && km > currentProfileKm) {
                    // КРИТИЧЕСКАЯ ПРОВЕРКА: пробег больше текущего одометра профиля.
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Пробег больше текущего")
                        .setMessage(
                            "Введённый пробег $km км больше текущего пробега " +
                                "автомобиля ($currentProfileKm км). Отметить как новый текущий пробег?"
                        )
                        .setPositiveButton("Да") { _, _ ->
                            // Согласие: сначала обновляем пробег профиля, затем отмечаем ТО.
                            prefs.setLastKm(prefs.currentProfileId, km)
                            prefs.setLastKmUpdate(prefs.currentProfileId, System.currentTimeMillis())
                            viewModel.markDone(km)
                            AppDialogs.toast(this, "Записано в историю, пробег обновлён: $km км")
                        }
                        .setNegativeButton("Нет") { confirmDialog, _ ->
                            confirmDialog.dismiss()
                            AppDialogs.toast(this, "Введите пробег не больше $currentProfileKm км")
                        }
                        .show()
                } else {
                    // Обычное сохранение без вопросов.
                    viewModel.markDone(km)
                    AppDialogs.toast(this, "Записано в историю")
                }
            }
        )
    }

    // =========================================================
    // Редактирование
    // =========================================================

    private fun showEditDialog() {
        val current = viewModel.state.value.item ?: return
        val useCase = com.mamblxon.autosense.domain.usecase.ServiceUseCase()

        val dialogBinding = DialogEditItemBinding.inflate(layoutInflater)
        dialogBinding.etName.setText(current.name)
        dialogBinding.etIntervalKm.setText(current.intervalKm.toString())
        dialogBinding.etIntervalMonths.setText(current.intervalMonths.toString())

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Редактировать работу")
            .setView(dialogBinding.root)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton("Отмена", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = dialogBinding.etName.text?.toString().orEmpty()
                val kmInput = dialogBinding.etIntervalKm.text?.toString().orEmpty()
                val monthsInput = dialogBinding.etIntervalMonths.text?.toString().orEmpty()

                var valid = true

                when (val r = useCase.validateName(name)) {
                    is com.mamblxon.autosense.domain.usecase.ValidationResult.Invalid -> {
                        dialogBinding.tilName.error = r.error
                        valid = false
                    }
                    else -> dialogBinding.tilName.error = null
                }

                when (val r = useCase.validateIntervalsPair(kmInput, monthsInput, current.currentKm)) {
                    is com.mamblxon.autosense.domain.usecase.ValidationResult.Invalid -> {
                        when {
                            r.error.contains("км", ignoreCase = true) -> {
                                dialogBinding.tilIntervalKm.error = r.error
                                dialogBinding.tilIntervalMonths.error = null
                            }
                            r.error.contains("месяц", ignoreCase = true) -> {
                                dialogBinding.tilIntervalMonths.error = r.error
                                dialogBinding.tilIntervalKm.error = null
                            }
                            else -> {
                                dialogBinding.tilIntervalKm.error = r.error
                                dialogBinding.tilIntervalMonths.error = " "
                            }
                        }
                        valid = false
                    }
                    else -> {
                        dialogBinding.tilIntervalKm.error = null
                        dialogBinding.tilIntervalMonths.error = null
                    }
                }

                if (!valid) return@setOnClickListener

                val km = useCase.parseIntOrNull(kmInput) ?: 0
                val months = useCase.parseIntOrNull(monthsInput) ?: 0

                viewModel.editItem(name.trim(), km, months)
                AppDialogs.toast(this, "Сохранено")
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    // =========================================================
    // Редактирование записи истории ТО
    // =========================================================

    /**
     * Диалог редактирования конкретной выполненной работы (ServiceRecord):
     * дата (MaterialDatePicker), пробег и стоимость (TYPE_CLASS_NUMBER), заметки.
     * Сохранение — через viewModel.editRecord(...); список истории обновится
     * автоматически через Flow observeHistory.
     */
    private fun showEditRecordDialog(record: ServiceRecord) {
        val item = viewModel.state.value.item
        val title = if (item != null) "Редактировать запись · ${item.name}" else "Редактировать запись"
        EditRecordDialog.show(
            context = this,
            record = record,
            title = title
        ) { rec, newDate, newKm, newCost, newNote ->
            viewModel.editRecord(
                record = rec,
                newDate = newDate,
                newKm = newKm,
                newCost = newCost,
                newNote = newNote
            )
        }
    }

    companion object {
        const val EXTRA_ITEM_ID = "extra_item_id"
    }
}