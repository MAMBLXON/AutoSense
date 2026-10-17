# ============================================================================
# AUTOSENSE RELEASE PROGUARD RULES
# Target Stack: Room 2.6+, Kotlin Serialization 1.6+, WorkManager 2.9+, MVVM
# ============================================================================

# --- ОБЩИЕ НАСТРОЙКИ ОПТИМИЗАЦИИ ---
# Включаем максимальный уровень оптимизации (5 passes)
-optimizationpasses 5
# Не удаляем неиспользуемые классы полностью (безопасность для DI/Reflection)
-dontusemixedcaseclassnames
# Не игнорируем предупреждения о неизвестных библиотеках
-dontskipnonpubliclibraryclasses
# Предупреждаем об ошибках, но не падаем билд (для отладки CI)
-ignorewarnings
# Оптимизируем ресурсы (удаляет неиспользуемый код)
-verbose

# --- KOTLIN & COROUTINES ---
# Сохраняем метаданные аннотаций (критично для @Serializable, @Entity, @Dao)
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**
-keepclassmembernames class kotlinx.** { volatile <fields>; }

# --- ROOM DATABASE ---
# КРИТИЧНО: Room использует Reflection для маппинга колонок. Без этих правил БД упадет.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**
-keep class com.mamblxon.autosense.data.local.** { *; }
-keep class com.mamblxon.autosense.domain.model.** { *; }

# --- KOTLIN SERIALIZATION (JSON Regulations/Catalog) ---
# КРИТИЧНО: Serializer генерируется компилятором, но вызывается через reflection.
-keepattributes RuntimeVisibleAnnotations
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class * { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.mamblxon.autosense.data.remote.**$$serializer { *; }
-keepclassmembers class com.mamblxon.autosense.data.remote.** { *** Companion; }
-keep class com.mamblxon.autosense.data.remote.CarRegulation { *; }
-keep class com.mamblxon.autosense.data.remote.CarModel { *; }
-keep class com.mamblxon.autosense.data.remote.ServiceSpec { *; }

# --- WORKMANAGER ---
# Worker создаётся системой по имени класса. Имена должны остаться неизменными.
-keep class com.mamblxon.autosense.notifications.** extends androidx.work.Worker { *; }
-keep class com.mamblxon.autosense.notifications.** extends androidx.work.ListenableWorker { *; }

# --- VIEWMODEL / LIVEDATA / FLOW ---
# ViewModel instantiated by factory via reflection in some setups
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }

# --- ENUMS (SortOrder, ServiceType, Urgency constants) ---
# Enums часто ломаются при обфускации, если используются в switch/valueOf
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }
-keep class com.mamblxon.autosense.ui.main.SortOrder { *; }
-keep class com.mamblxon.autosense.ui.main.ServiceType { *; }
-keep class com.mamblxon.autosense.ui.main.ServiceFilter { *; }

# --- LOGGING STRIPPING (Release Optimization) ---
# Удаляем все Log.* вызовы из release-билда для безопасности и скорости
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# --- THIRD PARTY LIBRARIES (Fallback safety nets) ---
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**
-dontwarn sun.misc.Unsafe
