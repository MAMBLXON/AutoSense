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
import kotlinx.coroutines.runBlocking
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
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private val adapter = GroupedServiceAdapter(
        onClick = { item -> hideKeyboard()
            Log.d("AutoSense", "onClick item id=${item.id} name=${item.name}")
            openDetail(item)
        },
        onLongClick = { item -> showItemMenu(item) }
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

        // Если currentProfileId не установлен, но профили есть в БД — выберем первый.
        // Это защита от старого состояния prefs: профиль создан, но id не сохранён.
        if (prefs.currentProfileId <= 0L) {
            val hasProfiles = kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    DefaultServiceRepository.get(applicationContext).countProfiles() > 0
                }
            }
            if (hasProfiles) {
                val firstProfile = kotlinx.coroutines.runBlocking {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        DefaultServiceRepository.get(applicationContext).observeProfiles()
                            .first().firstOrNull()
                    }
                }
                if (firstProfile != null) {
                    prefs.currentProfileId = firstProfile.id
                }
            } else {
                startActivity(Intent(this, com.mamblxon.autosense.ui.onboarding.OnboardingActivity::class.java))
                finish()
                return
            }
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        binding.fabUpdateKm.setOnClickListener { showUpdateKmDialog() }

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

        lifecycleScope.launch {
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
    }

    private fun renderEmptyState(st: ServiceUiState) {
        if (st.isEmpty && !st.isLoading) {
            binding.emptyState.isVisible = true
            binding.recycler.isVisible = false

            if (st.searchQuery.isNotBlank()) {
                binding.emptyTitle.text = "Ничего не найдено"
                binding.emptySubtitle.text = "По запросу \"${st.searchQuery}\""
            } else {
                binding.emptyTitle.text = "Ничего нет в этой категории"
                binding.emptySubtitle.text = "Попробуй выбрать другой фильтр"
            }
        } else {
            binding.emptyState.isVisible = false
            binding.recycler.isVisible = true
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
            actions = arrayOf("Отметить ТО выполненным", "Удалить"),
            onPick = { which ->
                when (which) {
                    0 -> showMarkDoneDialog(item)
                    1 -> AppDialogs.showConfirm(
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