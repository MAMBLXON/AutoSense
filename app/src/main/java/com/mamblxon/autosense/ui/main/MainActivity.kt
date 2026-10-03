package com.mamblxon.autosense.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import com.mamblxon.autosense.data.DefaultServiceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.mamblxon.autosense.R
import com.mamblxon.autosense.databinding.ActivityMainBinding
import com.mamblxon.autosense.domain.model.ServiceItem
import com.mamblxon.autosense.notifications.WorkScheduler
import com.mamblxon.autosense.prefs.AppPrefs
import com.mamblxon.autosense.ui.common.AppDialogs
import com.mamblxon.autosense.ui.main.detail.DetailActivity
import com.mamblxon.autosense.ui.profiles.ProfilesActivity
import com.mamblxon.autosense.ui.settings.SettingsActivity
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), SwipeActionHandler {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private val adapter = GroupedServiceAdapter(
        onClick = { item -> hideKeyboard()
            Log.d("AutoSense", "onClick item id=${item.id} name=${item.name}")
            openDetail(item)
        },
        onLongClick = { item -> showItemMenu(item) },
        swipeHandler = this
    )

    private val prefs by lazy { AppPrefs(this) }
    private val dateFormat by lazy {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    }

    private var searchView: SearchView? = null
    private var searchMenuItem: MenuItem? = null
    private var firstListRendered = false

    private var savedScrollPosition: Int = 0
    private var restoreScrollPending: Boolean = false
    private var scrollToTopOnNextRender: Boolean = false

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            AppDialogs.snackbarError(
                view = findViewById(android.R.id.content),
                text = "Уведомления отключены — напоминания не будут приходить",
                actionLabel = "Настройки"
            ) {
                openAppNotificationSettings()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Если currentProfileId не установлен, но профили есть в БД — выберем первый.
        // Это защита от старого состояния prefs: профиль создан, но id не сохранён.
        // Без runBlocking на главном потоке (грозит ANR): асинхронная инициализация,
        // MainViewModel сам отдаёт пустое состояние, пока profileId == 0.
        if (prefs.currentProfileId <= 0L) {
            lifecycleScope.launch {
                val firstProfile = withContext(Dispatchers.IO) {
                    DefaultServiceRepository.get(applicationContext).observeProfiles()
                        .first().firstOrNull()
                }
                if (firstProfile != null) {
                    prefs.currentProfileId = firstProfile.id
                } else {
                    startActivity(Intent(this@MainActivity, com.mamblxon.autosense.ui.onboarding.OnboardingActivity::class.java))
                    finish()
                }
            }
        }

        savedScrollPosition = savedInstanceState?.getInt(STATE_SCROLL_POS, 0) ?: 0
        restoreScrollPending = savedScrollPosition > 0

        setSupportActionBar(binding.toolbar)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.recycler.itemAnimator = DefaultItemAnimator().apply {
            addDuration = 200
            removeDuration = 200
            moveDuration = 200
            changeDuration = 200
        }
        binding.recycler.isClickable = true
        binding.recycler.isFocusable = true
        // Свайп-действия: влево — удаление с undo, вправо — редактирование.
        adapter.attachToRecyclerView(binding.recycler)

        binding.fabUpdateKm.setOnClickListener { showUpdateKmDialog() }

        binding.emptyAddButton.setOnClickListener { showAddItemDialog() }

        binding.chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when (checkedIds.firstOrNull()) {
                R.id.chipGreen -> ServiceFilter.GREEN
                R.id.chipYellow -> ServiceFilter.YELLOW
                R.id.chipRed -> ServiceFilter.RED
                else -> ServiceFilter.ALL
            }
            scrollToTopOnNextRender = true
            viewModel.onEvent(UiEvent.SetFilter(filter))
        }

        applyFilterFromIntent(intent)

        // Подписка на состояние — через repeatOnLifecycle(STARTED): корутина
        // отменяется на STOP и перезапускается на START, без утечек при
        // пересоздании Activity (поворот экрана) и без сбора мусора в фоне.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collectLatest { st ->
                        adapter.submitList(st.listItems) {
                            when {
                                restoreScrollPending && st.listItems.isNotEmpty() -> {
                                    restoreScrollPending = false
                                    binding.recycler.scrollToPosition(
                                        savedScrollPosition.coerceIn(0, st.listItems.size - 1)
                                    )
                                }
                                scrollToTopOnNextRender -> {
                                    scrollToTopOnNextRender = false
                                    binding.recycler.scrollToPosition(0)
                                }
                            }
                        }

                        binding.toolbar.title = when {
                            st.isLoading -> "Загрузка…"
                            st.error != null -> "Ошибка: ${st.error}"
                            st.currentCar.isBlank() -> "AutoSense"
                            else -> "AutoSense · ${st.currentCar}"
                        }
                        renderChips(st)
                        renderEmptyState(st)

                        if (!firstListRendered && st.listItems.isNotEmpty() && !st.isLoading) {
                            firstListRendered = true
                            binding.recycler.scheduleLayoutAnimation()
                        }
                    }
                }

                // Одноразовые сообщения (ошибки БД, подтверждения) — Snackbar вместо краша/тишины.
                launch {
                    viewModel.messages.collectLatest { msg ->
                        when (msg) {
                            is UiMessage.Error -> AppDialogs.snackbarError(
                                view = binding.root,
                                text = msg.text
                            )
                            is UiMessage.Info -> AppDialogs.snackbarInfo(
                                view = binding.root,
                                text = msg.text
                            )
                        }
                    }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val sv = searchView
                if (sv != null && !sv.isIconified) {
                    searchMenuItem?.collapseActionView()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        WorkScheduler.schedulePeriodic(this)
        WorkScheduler.runOnceNow(this)
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        // На случай, если пользователь сменил профиль через ProfilesActivity.
        // ViewModel не пересоздаётся — надо явно дёрнуть.
        viewModel.reloadForCurrentProfile()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!::binding.isInitialized) return
        val lm = binding.recycler.layoutManager as? LinearLayoutManager
        outState.putInt(STATE_SCROLL_POS, lm?.findFirstVisibleItemPosition() ?: 0)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyFilterFromIntent(intent)
    }

    // =========================================================
    // Меню
    // =========================================================

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val item = menu.findItem(R.id.action_search)
        searchMenuItem = item
        searchView = item?.actionView as? SearchView

        searchView?.apply {
            queryHint = "Поиск по названию"
            setInputType(InputType.TYPE_CLASS_TEXT)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setQuery(viewModel.state.value.searchQuery, false)

            isIconified = false
            isFocusable = true
            isFocusableInTouchMode = true

            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    clearFocus()
                    hideKeyboard()
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    scrollToTopOnNextRender = true
                    viewModel.onEvent(UiEvent.SetSearchQuery(newText.orEmpty()))
                    return true
                }
            })
        }

        item?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(menuItem: MenuItem): Boolean {
                searchView?.postDelayed({ forceShowKeyboard() }, 150L)
                return true
            }

            override fun onMenuItemActionCollapse(menuItem: MenuItem): Boolean {
                viewModel.onEvent(UiEvent.SetSearchQuery(""))
                hideKeyboard()
                return true
            }
        })

        item?.isVisible = true

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add_service -> {
                showAddItemDialog()
                true
            }
            R.id.action_issues -> {
                startActivity(
                    Intent(this, com.mamblxon.autosense.ui.issues.CarIssuesActivity::class.java)
                )
                true
            }
            R.id.action_history -> {
                startActivity(
                    Intent(this, com.mamblxon.autosense.ui.history.AllHistoryActivity::class.java)
                )
                true
            }
            R.id.action_check_now -> {
                WorkScheduler.runOnceNow(this)
                AppDialogs.toast(this, "Проверка запущена")
                true
            }
            R.id.action_filter -> {
                showTypeFilterDialog()
                true
            }
            R.id.action_sort -> {
                showSortDialog()
                true
            }
            R.id.action_change_car -> {
                startActivity(Intent(this, ProfilesActivity::class.java))
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // =========================================================
    // Рендер
    // =========================================================

    private fun renderChips(st: ServiceUiState) {
        binding.chipAll.text = "Все (${st.totals.all})"
        binding.chipGreen.text = "ОК (${st.totals.green})"
        binding.chipYellow.text = "Скоро (${st.totals.yellow})"
        binding.chipRed.text = "Просрочено (${st.totals.red})"

        val targetId = when (st.filter) {
            ServiceFilter.ALL -> R.id.chipAll
            ServiceFilter.GREEN -> R.id.chipGreen
            ServiceFilter.YELLOW -> R.id.chipYellow
            ServiceFilter.RED -> R.id.chipRed
        }
        if (binding.chipGroup.checkedChipId != targetId) {
            binding.chipGroup.check(targetId)
        }

        renderActiveFilterChips(st)
    }

    /** Чипы активных фильтров под toolbar: тип работ + сортировка (с кнопкой «×»). */
    private fun renderActiveFilterChips(st: ServiceUiState) {
        val group = binding.chipGroupActiveFilters
        group.removeAllViews()

        st.typeFilter.sorted().forEach { type ->
            group.addView(makeRemovableChip(type.label) {
                val next = viewModel.state.value.typeFilter - type
                scrollToTopOnNextRender = true
                viewModel.onEvent(UiEvent.SetTypeFilter(next))
            })
        }

        if (st.sortOrder != SortOrder.DATE_DESC) {
            group.addView(makeRemovableChip("Сортировка: ${st.sortOrder.label}") {
                viewModel.onEvent(UiEvent.SetSortOrder(SortOrder.DATE_DESC))
            })
        }

        binding.activeFiltersScroll.isVisible = group.childCount > 0
    }

    private fun makeRemovableChip(text: String, onRemove: () -> Unit): com.google.android.material.chip.Chip {
        return com.google.android.material.chip.Chip(this).apply {
            this.text = text
            isCheckable = false
            closeIcon = ContextCompat.getDrawable(this@MainActivity, androidx.appcompat.R.drawable.abc_ic_clear_material)
            // Material 1.12 не предоставляет setCloseIconOnClickListener —
            // реагируем на тап по правой части чипа (где рисуется иконка «×»).
            setOnClickListener { v ->
                if (v.width > 0 && lastTouchX >= v.width - closeIconZoneDp * resources.displayMetrics.density) {
                    onRemove()
                }
            }
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN,
                    android.view.MotionEvent.ACTION_MOVE -> lastTouchX = event.x
                    android.view.MotionEvent.ACTION_UP -> lastTouchX = event.x
                }
                false
            }
            setEnsureMinTouchTargetSize(false)
        }
    }

    /** Позиция последнего касания внутри чипа — для определения тапа по «×». */
    private var lastTouchX: Float = -1f

    /** Ширина зоны «×» справа чипа, по тапу в которой фильтр снимается (dp). */
    private val closeIconZoneDp = 32f

    /** Диалог «Фильтр»: чекбоксы по типам работ, несколько одновременно. */
    private fun showTypeFilterDialog() {
        lifecycleScope.launch {
            val counts = viewModel.itemTypeCounts()
            val types = ServiceType.entries.toTypedArray()
            val current = viewModel.state.value.typeFilter
            val initial = BooleanArray(types.size) { types[it] in current }
            val labels = types.map { t ->
                val c = counts[t] ?: 0
                if (c > 0) "${t.label} ($c)" else t.label
            }.toTypedArray()

            AppDialogs.showMultiChoice(
                context = this@MainActivity,
                title = "Фильтр по типу работ",
                items = labels,
                checked = initial,
                positiveText = "Применить",
                negativeText = "Сбросить",
                onApply = { picks ->
                    scrollToTopOnNextRender = true
                    viewModel.onEvent(UiEvent.SetTypeFilter(pickedTypes(types, picks)))
                },
                onReset = {
                    scrollToTopOnNextRender = true
                    viewModel.onEvent(UiEvent.SetTypeFilter(emptySet()))
                }
            )
        }
    }

    private fun pickedTypes(types: Array<ServiceType>, picks: BooleanArray): Set<ServiceType> =
        picks.indices.mapNotNull { i -> if (picks[i]) types[i] else null }.toSet()

    /** Меню сортировки: дата/пробег, направление; выбор сохраняется в SharedPreferences. */
    private fun showSortDialog() {
        val orders = SortOrder.entries.toTypedArray()
        val labels = orders.map { it.label }.toTypedArray()
        val current = viewModel.state.value.sortOrder

        AppDialogs.showSingleChoice(
            context = this,
            title = "Сортировка",
            options = labels,
            checkedIndex = orders.indexOf(current),
            onPick = { which ->
                val order = orders[which]
                if (order != current) {
                    scrollToTopOnNextRender = true
                    viewModel.onEvent(UiEvent.SetSortOrder(order))
                }
            }
        )
    }

    private fun renderEmptyState(st: ServiceUiState) {
        if (st.isEmpty && !st.isLoading) {
            binding.emptyState.isVisible = true
            binding.recycler.isVisible = false

            when {
                // В базе нет ни одной записи — показываем заглушку с кнопкой добавления.
                st.isDatabaseEmpty -> {
                    binding.emptyTitle.text = "Пока нет записей"
                    binding.emptySubtitle.text =
                        "Добавь первую работу по обслуживанию автомобиля, " +
                                "и здесь появится список ТО"
                    binding.emptyAddButton.isVisible = true
                }
                st.searchQuery.isNotBlank() -> {
                    binding.emptyTitle.text = "Ничего не найдено"
                    binding.emptySubtitle.text = "По запросу \"${st.searchQuery}\""
                    binding.emptyAddButton.isVisible = false
                }
                else -> {
                    binding.emptyTitle.text = "Ничего нет в этой категории"
                    binding.emptySubtitle.text = "Попробуй выбрать другой фильтр"
                    binding.emptyAddButton.isVisible = false
                }
            }
        } else {
            // Пока идёт загрузка (первая эмиссия Flow ещё не состоялась) — не показываем
            // ни список, ни заглушку: иначе при первом запуске на мгновение мелькнет
            // «Пока нет записей», хотя данные ещё не пришли.
            binding.emptyState.isVisible = false
            binding.recycler.isVisible = !st.isLoading
            binding.emptyAddButton.isVisible = false
        }
    }

    // =========================================================
    // Навигация
    // =========================================================

    private fun applyFilterFromIntent(intent: Intent?) {
        val name = intent?.getStringExtra(EXTRA_FILTER) ?: return
        val filter = runCatching { ServiceFilter.valueOf(name) }.getOrNull() ?: return
        viewModel.onEvent(UiEvent.SetFilter(filter))
    }

    private fun openDetail(item: ServiceItem) {
        val view = binding.recycler.findViewHolderForItemId(item.id)?.itemView
        val options = if (view != null) {
            androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(
                this,
                view,
                "service_card"
            )
        } else null

        val intent = Intent(this, DetailActivity::class.java)
            .putExtra(DetailActivity.EXTRA_ITEM_ID, item.id)

        try {
            if (options != null) startActivity(intent, options.toBundle())
            else startActivity(intent)
        } catch (t: Throwable) {
            Log.e("AutoSense", "Failed to start DetailActivity", t)
            AppDialogs.snackbarError(
                view = binding.root,
                text = "Не удалось открыть детали: ${t.message}"
            )
        }
    }

    // =========================================================
    // Диалоги
    // =========================================================

    /**
     * Единая точка «добавить запись»: открывается экран AddActivity
     * (валидация полей, обработка ошибок БД — внутри него).
     * Используется FAB и кнопкой на заглушке пустого списка.
     */
    private fun showAddItemDialog() {
        try {
            startActivity(Intent(this, com.mamblxon.autosense.ui.main.add.AddActivity::class.java))
        } catch (t: Throwable) {
            Log.e("AutoSense", "Failed to open AddActivity", t)
            AppDialogs.snackbarError(
                view = binding.root,
                text = "Не удалось открыть экран добавления"
            )
        }
    }

    // =========================================================
    // Свайп-действия (ItemTouchHelper)
    // =========================================================

    /** Свайп влево: удаляем запись и даём 4 секунды на «Отменить». */
    override fun onSwipeDelete(item: ServiceItem) {
        viewModel.deleteItem(item)
        AppDialogs.snackbarWithAction(
            view = binding.root,
            text = "Запись удалена",
            actionLabel = "Отменить",
            durationMs = 4000
        ) {
            // Пользователь нажал «Отменить» — восстанавливаем запись в базе.
            // undo-колбэк живёт дольше ViewModel при быстром закрытии экрана —
            // защищаемся от IllegalStateException («ViewModel is destroyed»).
            try {
                viewModel.onEvent(UiEvent.RestoreItem(item))
            } catch (t: Throwable) {
                Log.e("AutoSense", "Restore after undo failed (viewModel gone?)", t)
                AppDialogs.snackbarError(
                    view = findViewById(android.R.id.content),
                    text = "Не удалось восстановить запись"
                )
            }
        }
    }

    /** Свайп вправо: открываем диалог редактирования с заполненными полями. */
    override fun onSwipeEdit(item: ServiceItem) {
        showEditItemDialog(item)
    }

    private val editUseCase = com.mamblxon.autosense.domain.usecase.ServiceUseCase()

    /** Извлечение стоимости из заметки вида «Стоимость последнего ТО: 5000 ₽». */
    private val costRegex = Regex("Стоимость последнего ТО:\\s*(\\d+)")

    /**
     * Диалог редактирования записи: long click по элементу списка или свайп вправо.
     * Поля предзаполнены текущими данными; валидация — та же, что и при создании
     * (пункт 1В): название ≥ 2 символов, интервалы числовые и хотя бы один > 0,
     * дата в формате ДД.ММ.ГГГГ и не в будущем, стоимость — число > 0.
     * Кнопка «Сохранить» неактивна (серая), пока все поля не валидны.
     */
    private fun showEditItemDialog(item: ServiceItem) {
        val b = com.mamblxon.autosense.databinding.DialogEditItemBinding.inflate(layoutInflater)

        // Предзаполнение текущими данными записи.
        b.etName.setText(item.name)
        b.etIntervalKm.setText(if (item.intervalKm > 0) item.intervalKm.toString() else "")
        b.etIntervalMonths.setText(if (item.intervalMonths > 0) item.intervalMonths.toString() else "")
        b.etLastDate.setText(
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(item.lastServiceDateMillis))
        )
        costRegex.find(item.notes)?.groupValues?.getOrNull(1)?.let { b.etCost.setText(it) }

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Редактировать работу")
            .setView(b.root)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton("Отмена", null)
            .create()

        fun validateField(layout: TextInputLayout, result: com.mamblxon.autosense.domain.usecase.ValidationResult): Boolean {
            layout.error = if (result is com.mamblxon.autosense.domain.usecase.ValidationResult.Invalid) result.error else null
            return result !is com.mamblxon.autosense.domain.usecase.ValidationResult.Invalid
        }

        /** Полная проверка формы; возвращает true, если всё валидно. */
        fun validateForm(): Boolean {
            var ok = true
            ok = validateField(b.tilName, editUseCase.validateName(b.etName.text?.toString().orEmpty())) && ok
            val kmText = b.etIntervalKm.text?.toString().orEmpty()
            val monthsText = b.etIntervalMonths.text?.toString().orEmpty()
            val kmValid = validateField(b.tilIntervalKm, editUseCase.validateIntervalKm(kmText))
            val monthsValid = validateField(b.tilIntervalMonths, editUseCase.validateIntervalMonths(monthsText))
            ok = kmValid && monthsValid && ok
            if (kmValid && monthsValid && !editUseCase.hasPositiveInterval(kmText, monthsText)) {
                b.tilIntervalKm.error = "Хотя бы один интервал > 0"
                b.tilIntervalMonths.error = "Хотя бы один интервал > 0"
                ok = false
            }
            ok = validateField(b.tilLastDate, editUseCase.validateDateNotFuture(b.etLastDate.text?.toString().orEmpty())) && ok
            ok = validateField(b.tilCost, editUseCase.validateCost(b.etCost.text?.toString().orEmpty())) && ok
            return ok
        }

        // Живая валидация: «Сохранить» серая, пока все поля не заполнены корректно.
        val fields = listOf(b.etName, b.etIntervalKm, b.etIntervalMonths, b.etLastDate, b.etCost)
        val afterTextChanged = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.isEnabled =
                    validateForm()
            }
        }
        fields.forEach { it.addTextChangedListener(afterTextChanged) }

        dialog.setOnShowListener {
            // Валидируем сразу: если предзаполненные данные некорректны — кнопка неактивна.
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).isEnabled =
                validateForm()

            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    // Гвард на случай гонки: не сохраняем при невалидном вводе.
                    if (!validateForm()) return@setOnClickListener

                    val dateText = b.etLastDate.text?.toString().orEmpty().trim()
                    viewModel.onEvent(
                        UiEvent.EditItem(
                            item = item,
                            name = b.etName.text?.toString()?.trim().orEmpty(),
                            intervalKm = b.etIntervalKm.text?.toString()?.trim()?.toIntOrNull() ?: 0,
                            intervalMonths = b.etIntervalMonths.text?.toString()?.trim()?.toIntOrNull() ?: 0,
                            lastServiceDateMillis = editUseCase.parseDate(dateText)?.time
                                ?: item.lastServiceDateMillis,
                            costLine = b.etCost.text?.toString()?.trim()
                        )
                    )
                    dialog.dismiss()
                }
            // «Отмена» (или крест/тап вне) — диалог закрывается без изменений в базе.
        }

        dialog.show()
    }

    private fun showMarkDoneDialog(item: ServiceItem) {
        AppDialogs.showMarkDoneDialog(
            context = this,
            serviceName = item.name,
            carDisplayName = viewModel.state.value.currentCar,
            initialKm = item.currentKm,
            lastServiceKm = item.currentKm
        ) { km, cost, note ->
            viewModel.onEvent(UiEvent.MarkDone(item, km, cost, note))
        }
    }

    private fun showItemMenu(item: ServiceItem) {
        AppDialogs.showActions(
            context = this,
            title = item.name,
            actions = arrayOf("Редактировать", "Отметить ТО выполненным", "Удалить"),
            onPick = { which ->
                when (which) {
                    0 -> showEditItemDialog(item)
                    1 -> showMarkDoneDialog(item)
                    2 -> AppDialogs.showConfirm(
                        context = this,
                        title = "Удалить работу?",
                        message = "\"${item.name}\" и вся история по ней будут удалены.",
                        positiveText = "Удалить",
                        destructive = true,
                        onConfirm = { viewModel.onEvent(UiEvent.DeleteItem(item)) }
                    )
                }
            }
        )
    }

    private fun showUpdateKmDialog() {
        val profileId = prefs.currentProfileId
        if (profileId <= 0L) {
            AppDialogs.toast(this, "Сначала создай профиль")
            return
        }

        val car = viewModel.state.value.currentCar
        val useCase = com.mamblxon.autosense.domain.usecase.ServiceUseCase()

        // Fallback: берём пробег из любого item в текущем профиле
        val fallbackFromItems = viewModel.state.value.listItems
            .filterIsInstance<ServiceListItem.Row>()
            .firstOrNull()?.item?.currentKm ?: 0

        val knownKm = prefs.resolveKnownKm(profileId, fallbackFromItems)

        val lastUpdate = prefs.getLastKmUpdate(profileId)
        val lastUpdateText = if (lastUpdate > 0) dateFormat.format(Date(lastUpdate))
        else "ещё не обновлялся"

        val message = buildString {
            if (knownKm > 0) {
                append("Последний учёт: ").append(knownKm).append(" км · ").append(lastUpdateText)
            } else {
                append("Пробег ещё не задавался")
            }
        }

        AppDialogs.showInput(
            context = this,
            title = "Пробег",
            message = message,
            hint = "Новый пробег, км",
            initialValue = if (knownKm > 0) knownKm.toString() else "",
            inputType = InputType.TYPE_CLASS_NUMBER,
            iconRes = android.R.drawable.ic_menu_directions,
            positiveText = "Пересчитать",
            validate = { value ->
                val minKm = if (knownKm > 0) knownKm else null
                when (val r = useCase.validateKm(value, lastServiceKm = minKm)) {
                    is com.mamblxon.autosense.domain.usecase.ValidationResult.Invalid -> r.error
                    else -> null
                }
            },
            onConfirm = { value ->
                val newKm = value.toInt()
                viewModel.onEvent(UiEvent.UpdateKm(newKm))

                val toastText = when {
                    knownKm == 0 -> "Пробег установлен: $newKm км"
                    newKm == knownKm -> "Пробег не изменился: $newKm км"
                    else -> "Пробег: $knownKm → $newKm км (+${newKm - knownKm})"
                }
                AppDialogs.toast(this, toastText)
            }
        )
    }

    // =========================================================
    // Клавиатура
    // =========================================================

    private fun forceShowKeyboard() {
        val sv = searchView ?: return
        sv.isIconified = false
        sv.requestFocus()
        val target = sv.findFocus() ?: sv

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT)

        try {
            val controller = WindowCompat.getInsetsController(window, sv)
            controller.show(WindowInsetsCompat.Type.ime())
        } catch (_: Throwable) { /* ignore */ }

        sv.postDelayed({
            val imm2 = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            if (imm2 != null && !imm2.isAcceptingText) {
                imm2.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT)
            }
        }, 120L)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.root.windowToken, 0)
        currentFocus?.clearFocus()
    }

    // =========================================================
    // Системные настройки
    // =========================================================

    private fun openAppNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        try {
            startActivity(intent)
        } catch (_: Throwable) {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.fromParts("package", packageName, null))
            startActivity(fallback)
        }
    }

    // =========================================================
    // Разрешения
    // =========================================================

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted && !prefs.notifPermissionAsked) {
            prefs.notifPermissionAsked = true
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_FILTER = "extra_filter"
        private const val STATE_SCROLL_POS = "state_scroll_pos"
    }
}