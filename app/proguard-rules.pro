# ============================================================================
# proguard-rules.pro — AutoSense (com.mamblxon.autosense)
# Стек: Room 2.6.1, Kotlin Serialization 1.6.0, Jetpack Compose + Material3,
#       WorkManager 2.9.0, Navigation Compose 2.7.7
# Подключается в app/build.gradle.kts через proguardFiles(...)
# ============================================================================

# ----------------------------------------------------------------------------
# 6. ОБЩИЕ НАСТРОЙКИ
# ----------------------------------------------------------------------------

# Пять проходов оптимизации bytecode (эффективно для классического ProGuard;
# R8 игнорирует эту директиву, но её наличие безвредно и требует ТЗ)
-optimizationpasses 5

# Атрибут Signature хранит информацию о дженериках (Flow<List<ServiceItem>>,
# KSerializer<T> и т.п.) — без него рефлексия по параметризованным типам сломается
-keepattributes Signature

# Имена параметров и локальных переменных нужны Compose-компилятору и named-arguments
-keepattributes LocalVariableTable,LocalVariableTypeTable

# Аннотации уровня runtime: по ним работают Room (@Entity/@Dao),
# kotlinx.serialization (@Serializable) и Compose (@Composable/@Stable)
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault

# Вложенные классы/методы — необходимы для генерируемых $$serializer
# и companion-объектов
-keepattributes InnerClasses,EnclosingMethod

# Строки ресурсов (R$string) и сам R-класс не должны вырезаться оптимизатором
-keep class **.R { *; }
-keep class **.R$string { *; }

# Полное удаление логирования из release-сборки: все вызовы Log.* считаются
# не имеющими побочных эффектов, поэтому вместе с ними вырезаются и
# конкатенации строк-аргументов (дополнительная экономия метода/профиля)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static int println(...);
    public static int print(...);
}

# Аналогично убираем println/stdout из Kotlin-кода
-assumenosideeffects class java.io.PrintStream {
    public *** print(...);
    public *** println(...);
}

# Kotlin stdlib ссылается на аннотации/классы, которых может не быть в classpath
-dontwarn kotlin.**
-dontwarn org.jetbrains.annotations.**

# ----------------------------------------------------------------------------
# 1. ROOM DATABASE 2.6.1
# ----------------------------------------------------------------------------

# Все @Entity-классы (CarProfile, ServiceItem, ServiceRecord): Room-код
# генерирует bind-вызовы, завязанные на имена классов и полей. Сохраняем
# и сам класс целиком, и аннотацию на нём
-keep @androidx.room.Entity class * { *; }

# Интерфейсы с @Dao (CarProfileDao, ServiceDao, ServiceRecordDao): Room
# создаёт реализации *_Impl, методы которых маппятся на аннотированные
# методы интерфейса по имени и сигнатуре
-keep @interface androidx.room.Dao
-keep @androidx.room.Dao interface * { *; }

# Наследники RoomDatabase (AppDatabase): Room-фабрика инстанцирует
# сгенерированный AppDatabase_Impl* по имени класса через рефлексию,
# поэтому сохраняем и базовый класс, и конструктор наследников
-keep abstract class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase {
    <init>();
    <fields>;
}
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public static ** newInstance(...);
}

# Миграции не трогаем: Migration-классы/объекты находят по имени при
# открытии БД со старой версией схемы (Room.onOpen -> migrate()).
# В Kotlin миграции — это объекты (AutoMigration/Migration$Companion),
# поэтому фиксируем и implements-, и extends-форму
-keep class * implements androidx.room.migration.Migration { *; }
-keep class androidx.room.migration.** { *; }

# TypeConverters вызываются сгенерированным кодом по именам методов
-keepclassmembers class * {
    @androidx.room.TypeConverter <methods>;
}

# Callback и DatabaseInfo используются Room во время lifecycle-событий БД
-keep class androidx.room.RoomDatabase$Callback { *; }
-keep class * extends androidx.room.RoomDatabase$Callback { *; }

# Предупреждения об optional-классах Room, отсутствующих без исходников
# (paging, rxjava2-адаптеры в проект не включены)
-dontwarn androidx.room.paging.**
-dontwarn androidx.sqlite.db.framework.**

# ----------------------------------------------------------------------------
# 2. KOTLIN SERIALIZATION 1.6.0
# ----------------------------------------------------------------------------

# Правило из официальной документации kotlinx.serialization:
# сохраняем инстанс Companion-объекта и синтетический метод serializer()
# у всех классов, помеченных @Serializable (CarCatalog, CarRegulation и др.)
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    # companion-объект сериализатора обязателен для Json.decodeFromString
    static <1>$Companion Companion;
    # статический доступ к serializer() напрямую по имени класса-носителя
    static kotlinx.serialization.KSerializer serializer(...);
}

# Сам companion-объект: @Serializable-классы вызывают Serializer.serializer()
# через Companion, его members тоже фиксируем
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Генерируемые плагином классы-сериализаторы $$serializer — их удалять нельзя,
# иначе рантайм не найдёт сериализатор по имени <Class>$$serializer
-keepclassmembers class **$$serializer {
    *;
}

# Инстансы Companion-объектов (public static поле COMPANION-эквивалент)
-keepclassmembers class com.mamblxon.autosense.** {
    *** Companion;
}

# Внутренние классы пакета json.* модуля kotlinx-serialization-json
# (Json, JsonElement, JsonObject, десериализаторы форматов) — публичный API,
# используется напрямую и через reflection-поиск фабрик
-keep class kotlinx.serialization.json.** { *; }
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}

# Ядро serialization: интерфейсы KSerializer/SerialDescriptor и builtins-сериализаторы
-keep interface kotlinx.serialization.KSerializer { *; }
-keep class kotlinx.serialization.builtins.** { *; }
-keep class kotlinx.serialization.SerializersKt* { *; }

# Self-upcasting warnings от internal-API serialization при minify
-dontwarn kotlinx.serialization.internal.**
-dontwarn kotlinx.serialization.**

# ----------------------------------------------------------------------------
# 3. JETPACK COMPOSE + MATERIAL3
# ----------------------------------------------------------------------------

# Compose не поддерживает полноценную минификацию composable-структур:
# стандартная рекомендация Google — не переименовывать и не удалять классы,
# содержащие @Composable-функции, чтобы не ломать group-keys рантайма
-keep @androidx.compose.runtime.Composable class * { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Синтетические перегрузки composable-функций с параметрами
# ($composer, $changed, $default) добавляются компилятором на call-site —
# их сигнатуры должны остаться стабильными
-keepclassmembers,includedescriptorclasses class * {
    *** *(androidx.compose.runtime.Composer, int, ...);
}

# Аннотации stability-inference участвуют в решениях рантайма о реконпозиции
-keep @interface androidx.compose.runtime.Stable
-keep @interface androidx.compose.runtime.Immutable
-keep @interface androidx.compose.runtime.Composable

# Material3: тематические классы и иконки подгружаются динамически
# (DynamicTonalPalette, IconButton и т.д.) — фиксируем весь пакет
-keep class androidx.compose.material3.** { *; }

# Внутренний механизм Compose-лямбд (remembered-коллбэки реализуют этот интерфейс)
-keep interface androidx.compose.runtime.internal.ComposableLambda { *; }
-keep class androidx.compose.runtime.internal.** { *; }

# ui-пакет приложения целиком состоит из composable'ов — фиксируем,
# чтобы R8 не вырезал «неиспользуемые» private composable-функции
-keep class com.mamblxon.autosense.ui.** { *; }

# Optional-зависимости Compose UI (emoji2, window, graphics-shapes),
# которых нет в classpath — глушим предупреждения
-dontwarn androidx.window.**
-dontwarn androidx.emoji2.text.**
-dontwarn androidx.graphics.shapes.**

# ----------------------------------------------------------------------------
# 4. WORKMANAGER 2.9.0
# ----------------------------------------------------------------------------

# Worker-классы (ServiceCheckWorker) инстанцируются WorkManager по имени
# класса из внутренней таблицы работы через рефлексию — публичный
# конструктор (Context, WorkerParameters) обязан сохраниться
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# RxWorker/CoroutineWorker-наследники (если появятся) — та же схема конструктора
-keep class * extends androidx.work.rxjava3.RxWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Result-фабрика Worker.Result.success()/failure() вызывается из doWork()
-keepclassmembers class androidx.work.Worker$Result { *; }
-keepclassmembers class androidx.work.ListenableWorker$Result { *; }

# Политики backoff/replace используются как enum-константы в WorkRequest.Builder
-keepclassmembers enum androidx.work.BackoffPolicy { *; }
-keepclassmembers enum androidx.work.ExistingWorkPolicy { *; }
-keepclassmembers enum androidx.work.ExistingPeriodicWorkPolicy { *; }

# SnoozeReceiver: BroadcastReceiver регистрируется в AndroidManifest и
# инстанцируется системой по имени класса (Class.forName) — имя и
# конструктор без аргументов обязаны сохраниться
-keep class com.mamblxon.autosense.notifications.SnoozeReceiver { *; }
-keep class * extends android.content.BroadcastReceiver {
    public <init>();
}

# Внутренности WorkManager тянут Guava/Firebase-JobDispatcher, которых может не быть
-dontwarn androidx.work.impl.**
-dontwarn com.google.common.util.concurrent.**

# ----------------------------------------------------------------------------
# 5. NAVIGATION COMPOSE 2.7.7
# ----------------------------------------------------------------------------

# Классы навигационного графа загружаются NavController рефлексивно при
# инфляции destinations — сохраняем ядро navigation-common
-keep class androidx.navigation.NavGraph { *; }
-keep class androidx.navigation.NavGraph$Navigator { *; }
-keep class androidx.navigation.NavDestination { *; }
-keep class androidx.navigation.NavOptions { *; }
-keep class androidx.navigation.Navigator { *; }
-keep class androidx.navigation.NavController { *; }

# Маршруты NavHost (composable("home"), composable("car/{id}")) сравниваются
# по строковым literal'ам. String-константы маршрутов лежат в объектах/константах
# приложения — фиксируем sealed-иерархии экранов и их поля route, чтобы R8
# не схлопнул/не переименовал носителей строковых литералов
-keepclassmembers class com.mamblxon.autosense.ui.** {
    java.lang.String ROUTE;
    java.lang.String route;
}
-keepnames class com.mamblxon.autosense.***$*

# Аргументы deep-link извлекаются по именам плейсхолдеров ("{id}") —
# аннотированные поля/методы deep-link API сохраняем
-keepclassmembers class * {
    @androidx.navigation.NavDeepLink <fields>;
    @androidx.navigation.NavDeepLink <methods>;
}

# Safe Args (если подключается позже): генерируемые Directions/Args-классы
# передаются в Bundle по имени класса
-keep class * implements androidx.navigation.NavOptionsBuilder { *; }

# Внутренние optional-классы navigation (fragment-ktx связки) без исходников
-dontwarn androidx.navigation.fragment.**
-dontwarn androidx.navigation.compose.**

# ----------------------------------------------------------------------------
# ДОПОЛНИТЕЛЬНО: КОРУТИНЫ / LIFECYCLE / ПАКЕТЫ БЕЗ ИСТОЧНИКОВ
# ----------------------------------------------------------------------------

# Корутинные continuation-классы: volatile-поля читаются движком отмены
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ViewModel создаётся Factory-рефлексией — пустой конструктор сохраняем.
# AndroidViewModel-наследники (DetailViewModel и др.) создаются через
# ViewModelProvider.Factory по имени класса — фиксируем сами классы
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>();
}
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }

# Material/AppCompat: LayoutInflater инфлирует view-классы из XML рефлексивно
# по сигнатуре конструктора (Context, AttributeSet[, int]) — конструкторы обязаны остаться
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
-keepnames class com.google.android.material.** { *; }
-keep class androidx.appcompat.widget.** { *; }

# Enum values()/valueOf() — нужны встроенному EnumSerializer из kotlinx.serialization,
# а также SortOrder.valueOf() в AppPrefs (парсинг порядка сортировки из SharedPreferences)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
