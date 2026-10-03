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
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mamblxon.autosense.R
import com.mamblxon.autosense.databinding.ActivityDetailBinding
import com.mamblxon.autosense.databinding.DialogEditItemBinding
import com.mamblxon.autosense.export.CsvExporter
import com.mamblxon.autosense.ui.common.AppDialogs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private val adapter = ServiceRecordAdapter()

    private var itemId: Long = -1L
    private var lastCsv: String? = null
    private var finished = false

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

        lifecycleScope.launch {
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
                    "${it.car} · каждые ${it.intervalKm} км / ${it.intervalMonths} мес"
                } ?: ""
                adapter.submitList(st.history)
                binding.tvEmpty.isVisible = st.history.isEmpty()
                binding.btnMarkDone.isEnabled = st.item != null && !st.isLoading
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
                when (val r = useCase.validateKm(value, lastServiceKm = item.currentKm)) {
                    is com.mamblxon.autosense.domain.usecase.ValidationResult.Invalid -> r.error
                    else -> null
                }
            },
            onConfirm = { value ->
                viewModel.markDone(value.toInt())
                AppDialogs.toast(this, "Записано в историю")
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

                when (val r = useCase.validateIntervalsPair(kmInput, monthsInput)) {
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

    companion object {
        const val EXTRA_ITEM_ID = "extra_item_id"
    }
}